package app.capnative.pdfviewer

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal data class MaterializedPdf(
    val file: File,
    val ownsFile: Boolean,
)

internal object PdfFiles {
    fun materialize(context: Context, source: PdfSource, headers: Map<String, String>): MaterializedPdf {
        return when (source) {
            is PdfSource.Remote -> MaterializedPdf(download(context, source.url, headers), ownsFile = true)
            is PdfSource.LocalFile -> MaterializedPdf(requireReadable(source.path), ownsFile = false)
            is PdfSource.Content -> MaterializedPdf(copyContent(context, source.uri), ownsFile = true)
        }
    }

    fun copyToCache(context: Context, source: File): File {
        val destination = createCacheFile(context)
        source.inputStream().use { input ->
            copyLimited(input, destination)
        }
        return destination
    }

    private fun requireReadable(path: String): File {
        val file = File(path)
        if (!file.isFile || !file.canRead()) {
            throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF file could not be read.")
        }
        return file
    }

    private fun download(context: Context, url: String, headers: Map<String, String>): File {
        val destination = createCacheFile(context)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/pdf,*/*")
        }
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF could not be downloaded (HTTP $code).")
            }
            val length = connection.contentLengthLong
            if (length > MAX_PDF_BYTES) {
                throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF is larger than 200 MB.")
            }
            connection.inputStream.use { input ->
                copyLimited(input, destination)
            }
            if (destination.length() == 0L) {
                throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF download was empty.")
            }
            return destination
        } catch (error: PdfRequestException) {
            destination.delete()
            throw error
        } catch (_: Exception) {
            destination.delete()
            throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF could not be downloaded.")
        } finally {
            connection.disconnect()
        }
    }

    private fun copyContent(context: Context, uri: String): File {
        val destination = createCacheFile(context)
        try {
            val stream = context.contentResolver.openInputStream(Uri.parse(uri))
                ?: throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF file could not be read.")
            stream.use { input ->
                copyLimited(input, destination)
            }
            if (destination.length() == 0L) {
                throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF file could not be read.")
            }
            return destination
        } catch (error: PdfRequestException) {
            destination.delete()
            throw error
        } catch (_: Exception) {
            destination.delete()
            throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF file could not be read.")
        }
    }

    private fun copyLimited(input: InputStream, destination: File) {
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                total += read.toLong()
                if (total > MAX_PDF_BYTES) {
                    throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF is larger than 200 MB.")
                }
                output.write(buffer, 0, read)
            }
        }
    }

    private fun createCacheFile(context: Context): File {
        val directory = File(context.cacheDir, "pdf-viewer")
        if (!directory.exists() && !directory.mkdirs()) {
            throw PdfRequestException(PdfError.LOAD_ERROR, "The PDF could not be saved for viewing.")
        }
        return File(directory, "${UUID.randomUUID()}.pdf")
    }
}
