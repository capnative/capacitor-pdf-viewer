package app.capnative.pdfviewer

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * In-app PDF screen backed by the platform [PdfRenderer].
 *
 * AndroidX `pdf-viewer-fragment` is still beta and requires a higher min SDK
 * than Capacitor, so this activity renders pages itself. Pinch-zoom and pan
 * live in [PdfPageView]; previous, next, and horizontal swipes change pages.
 */
class PdfViewerActivity : AppCompatActivity() {
    private lateinit var request: PdfOpenRequest
    private var sessionToken: PdfSession.Token? = null
    private lateinit var pageView: PdfPageView
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var pageLabel: TextView
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var shareButton: Button

    private var renderExecutor: ExecutorService? = null
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var file: File? = null
    private var ownsFile = false
    private var pageCount = 0
    private var currentPage = 1
    private var lastPublishedPage = 0
    private var opened = false
    private var failed = false
    private var notified = false
    private var destroyed = false
    private var started = false
    private var renderGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = PdfSession.token
        val pending = PdfSession.request
        if (token == null || pending == null) {
            finish()
            return
        }
        sessionToken = token
        request = pending
        currentPage = pending.page
        PdfSession.closeRequested = { finish() }
        if (PdfSession.pendingClose) {
            finish()
            return
        }

        window.decorView.setBackgroundColor(Color.parseColor("#1C1C1E"))
        setContentView(R.layout.activity_pdf_viewer)
        pageView = findViewById(R.id.pdf_viewer_page)
        progress = findViewById(R.id.pdf_viewer_progress)
        titleView = findViewById(R.id.pdf_viewer_title)
        pageLabel = findViewById(R.id.pdf_viewer_page_label)
        previousButton = findViewById(R.id.pdf_viewer_previous)
        nextButton = findViewById(R.id.pdf_viewer_next)
        shareButton = findViewById(R.id.pdf_viewer_share)

        titleView.text = request.displayTitle
        findViewById<Button>(R.id.pdf_viewer_close).setOnClickListener { finish() }
        shareButton.setOnClickListener { share() }
        previousButton.setOnClickListener { showPage(currentPage - 2) }
        nextButton.setOnClickListener { showPage(currentPage) }
        pageView.onHorizontalSwipe = { direction -> showPage(currentPage - 1 + direction) }
        pageView.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val newWidth = right - left
            val oldWidth = oldRight - oldLeft
            if (!started && newWidth > 0 && pageView.height > 0) {
                started = true
                beginLoad(newWidth)
            } else if (opened && oldWidth > 0 && newWidth > 0 && kotlin.math.abs(newWidth - oldWidth) > 48) {
                renderCurrentPage()
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        renderGeneration += 1
        val pool = renderExecutor
        val openRenderer = renderer
        val openDescriptor = descriptor
        renderer = null
        descriptor = null
        if (pool != null) {
            pool.execute {
                closeQuietly(openRenderer)
                closeQuietly(openDescriptor)
            }
            pool.shutdown()
        } else {
            closeQuietly(openRenderer)
            closeQuietly(openDescriptor)
        }
        pageView.releaseBitmap()
        if (ownsFile) {
            file?.delete()
        }
        if (!notified) {
            notified = true
            if (opened) {
                PdfSession.callbacks?.onClosed(currentPage, pageCount)
            } else if (!failed) {
                PdfSession.callbacks?.onCancelled()
            }
        }
        sessionToken?.let { PdfSession.markClosed(it) }
        super.onDestroy()
    }

    private fun beginLoad(viewWidth: Int) {
        progress.visibility = View.VISIBLE
        executor().execute {
            var ownedFile: File? = null
            var document: PdfRenderer? = null
            var pfd: ParcelFileDescriptor? = null
            try {
                val materialized = PdfFiles.materialize(this, request.source, request.headers)
                if (materialized.ownsFile) {
                    ownedFile = materialized.file
                }
                if (destroyed) {
                    throw PdfRequestException(PdfError.CANCELLED, "The viewer was closed before the PDF finished loading.")
                }
                val openedDescriptor = ParcelFileDescriptor.open(
                    materialized.file,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                pfd = openedDescriptor
                document = openDocument(openedDescriptor)
                val pageTotal = document?.pageCount ?: 0
                if (pageTotal <= 0) {
                    throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF has no pages.")
                }
                if (request.page > pageTotal) {
                    throw PdfRequestException(
                        PdfError.INVALID_PAGE,
                        "page is greater than the number of pages in the PDF.",
                    )
                }
                val bitmap = renderPage(requireNotNull(document), request.page - 1, viewWidth)
                val openDocument = requireNotNull(document)
                val openDescriptor = requireNotNull(pfd)
                document = null
                pfd = null
                ownedFile = null
                runOnUiThread {
                    if (destroyed) {
                        bitmap.recycle()
                        closeQuietly(openDocument)
                        closeQuietly(openDescriptor)
                        if (materialized.ownsFile) {
                            materialized.file.delete()
                        }
                        return@runOnUiThread
                    }
                    renderer = openDocument
                    descriptor = openDescriptor
                    file = materialized.file
                    ownsFile = materialized.ownsFile
                    pageCount = pageTotal
                    currentPage = request.page
                    opened = true
                    pageView.setBitmap(bitmap)
                    progress.visibility = View.GONE
                    updateChrome()
                    publishPage()
                    PdfSession.callbacks?.onOpened()
                }
            } catch (error: PdfRequestException) {
                closeQuietly(document)
                closeQuietly(pfd)
                ownedFile?.delete()
                if (error.code != PdfError.CANCELLED) {
                    runOnUiThread { fail(error.message ?: "The PDF could not be opened.", error.code) }
                }
            } catch (_: Exception) {
                closeQuietly(document)
                closeQuietly(pfd)
                ownedFile?.delete()
                runOnUiThread { fail("The PDF could not be opened.", PdfError.LOAD_ERROR) }
            }
        }
    }

    private fun openDocument(descriptor: ParcelFileDescriptor): PdfRenderer {
        return try {
            PdfRenderer(descriptor)
        } catch (_: SecurityException) {
            throw PdfRequestException(PdfError.PASSWORD_PROTECTED, "This PDF is password protected.")
        } catch (_: IOException) {
            throw PdfRequestException(
                PdfError.LOAD_ERROR,
                "The PDF could not be opened. It may be corrupt or not a PDF.",
            )
        }
    }

    private fun showPage(index: Int) {
        if (!opened || index < 0 || index >= pageCount || index == currentPage - 1) {
            return
        }
        val previous = currentPage
        currentPage = index + 1
        renderCurrentPage(onFailure = { currentPage = previous })
    }

    private fun renderCurrentPage(onFailure: () -> Unit = {}) {
        val document = renderer ?: return
        val width = pageView.width
        if (width <= 0) {
            return
        }
        val index = currentPage - 1
        val generation = ++renderGeneration
        progress.visibility = View.VISIBLE
        executor().execute {
            try {
                val bitmap = renderPage(document, index, width)
                runOnUiThread {
                    if (destroyed || generation != renderGeneration) {
                        bitmap.recycle()
                        return@runOnUiThread
                    }
                    pageView.setBitmap(bitmap)
                    progress.visibility = View.GONE
                    updateChrome()
                    publishPage()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (destroyed || generation != renderGeneration) {
                        return@runOnUiThread
                    }
                    onFailure()
                    progress.visibility = View.GONE
                    updateChrome()
                    Toast.makeText(this, "The page could not be rendered.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderPage(document: PdfRenderer, index: Int, viewWidth: Int): Bitmap {
        val page = document.openPage(index)
        try {
            val safeWidth = viewWidth.coerceAtLeast(1)
            var width = min(safeWidth * 2, 2048)
            val aspect = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
            var height = (width * aspect).toInt().coerceAtLeast(1)
            if (height > 4096) {
                height = 4096
                width = (height / aspect).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            @Suppress("DEPRECATION")
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    private fun publishPage() {
        if (!opened || currentPage == lastPublishedPage) {
            return
        }
        lastPublishedPage = currentPage
        PdfSession.callbacks?.onPageChanged(currentPage, pageCount)
    }

    private fun updateChrome() {
        pageLabel.text = if (pageCount > 0) "$currentPage / $pageCount" else ""
        previousButton.isEnabled = opened && currentPage > 1
        nextButton.isEnabled = opened && currentPage < pageCount
        shareButton.isEnabled = opened && file != null
        pageView.contentDescription = if (pageCount > 0) "Page $currentPage of $pageCount" else "PDF page"
    }

    private fun share() {
        val current = file ?: return
        try {
            val uri = try {
                FileProvider.getUriForFile(this, authority(), current)
            } catch (_: IllegalArgumentException) {
                FileProvider.getUriForFile(this, authority(), PdfFiles.copyToCache(this, current))
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("PDF", uri)
            }
            startActivity(Intent.createChooser(send, "Share PDF"))
        } catch (_: Exception) {
            Toast.makeText(this, "The PDF could not be shared.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fail(message: String, code: String) {
        if (notified || destroyed) {
            return
        }
        failed = true
        notified = true
        PdfSession.callbacks?.onFailed(message, code)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun executor(): ExecutorService {
        val existing = renderExecutor
        if (existing != null) {
            return existing
        }
        val created = Executors.newSingleThreadExecutor()
        renderExecutor = created
        return created
    }

    private fun authority(): String = "$packageName.pdfviewer.fileprovider"

    private fun closeQuietly(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
    }
}
