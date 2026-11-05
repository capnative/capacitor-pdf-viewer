package app.capnative.pdfviewer

import android.content.Intent
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject

@CapacitorPlugin(name = "PdfViewer")
class PdfViewerPlugin : Plugin() {
    @PluginMethod
    fun open(call: PluginCall) {
        val request = try {
            PdfRequests.parse(
                url = rawOption(call, "url"),
                title = rawOption(call, "title"),
                page = rawOption(call, "page"),
                headers = readHeaders(call),
            )
        } catch (error: PdfRequestException) {
            call.reject(error.message ?: "The PDF could not be opened.", error.code)
            return
        } catch (_: Exception) {
            call.reject("The PDF options could not be read.", PdfError.INVALID_SOURCE)
            return
        }

        val host = activity
        if (host == null) {
            call.reject("The viewer could not be presented.", PdfError.UNAVAILABLE)
            return
        }

        host.runOnUiThread {
            if (!PdfSession.tryBeginOpen(request, SessionCallbacks(call))) {
                call.reject("A PDF viewer is already open. Call close() first.", PdfError.ALREADY_OPEN)
                return@runOnUiThread
            }
            try {
                host.startActivity(Intent(host, PdfViewerActivity::class.java))
            } catch (_: Exception) {
                PdfSession.token?.let { PdfSession.markClosed(it) }
                call.reject("The viewer could not be presented.", PdfError.UNAVAILABLE)
            }
        }
    }

    @PluginMethod
    fun close(call: PluginCall) {
        val host = activity
        if (host == null) {
            PdfSession.requestClose()
            call.resolve()
            return
        }
        host.runOnUiThread {
            PdfSession.requestClose()
            call.resolve()
        }
    }

    override fun handleOnDestroy() {
        activity?.runOnUiThread { PdfSession.requestClose() } ?: PdfSession.requestClose()
        super.handleOnDestroy()
    }

    private fun rawOption(call: PluginCall, name: String): Any? {
        if (!call.data.has(name) || call.data.isNull(name)) {
            return null
        }
        return call.data.get(name)
    }

    private fun readHeaders(call: PluginCall): Map<String, Any?>? {
        if (!call.data.has("headers") || call.data.isNull("headers")) {
            return null
        }
        val value = call.data.get("headers")
        if (value !is JSONObject) {
            throw PdfRequestException(PdfError.INVALID_HEADERS, "headers must be an object whose values are strings.")
        }
        val headers = linkedMapOf<String, Any?>()
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            headers[key] = if (value.isNull(key)) null else value.get(key)
        }
        return headers
    }

    private inner class SessionCallbacks(
        private val call: PluginCall,
    ) : PdfSession.Callbacks {
        private var settled = false

        override fun onOpened() {
            if (settled) {
                return
            }
            settled = true
            call.resolve()
        }

        override fun onFailed(message: String, code: String) {
            if (settled) {
                return
            }
            settled = true
            call.reject(message, code)
        }

        override fun onCancelled() {
            if (settled) {
                return
            }
            settled = true
            call.reject("The viewer was closed before the PDF finished loading.", PdfError.CANCELLED)
        }

        override fun onPageChanged(page: Int, pageCount: Int) {
            notifyListeners(
                "pageChanged",
                JSObject().apply {
                    put("page", page)
                    put("pageCount", pageCount)
                },
            )
        }

        override fun onClosed(page: Int, pageCount: Int) {
            notifyListeners(
                "closed",
                JSObject().apply {
                    put("page", page)
                    put("pageCount", pageCount)
                },
            )
        }
    }
}
