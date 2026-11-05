package app.capnative.pdfviewer

internal object PdfSession {
    class Token

    interface Callbacks {
        fun onOpened()

        fun onFailed(message: String, code: String)

        fun onCancelled()

        fun onPageChanged(page: Int, pageCount: Int)

        fun onClosed(page: Int, pageCount: Int)
    }

    var request: PdfOpenRequest? = null
        private set
    var callbacks: Callbacks? = null
        private set
    var token: Token? = null
        private set
    var closeRequested: (() -> Unit)? = null
    var pendingClose: Boolean = false

    private var open: Boolean = false

    fun tryBeginOpen(request: PdfOpenRequest, callbacks: Callbacks): Boolean {
        if (open) {
            return false
        }
        open = true
        pendingClose = false
        this.request = request
        this.callbacks = callbacks
        this.token = Token()
        this.closeRequested = null
        return true
    }

    fun requestClose() {
        val closer = closeRequested
        if (closer != null) {
            closer()
        } else {
            pendingClose = true
        }
    }

    fun markClosed(token: Token) {
        if (this.token !== token) {
            return
        }
        open = false
        pendingClose = false
        this.token = null
        request = null
        callbacks = null
        closeRequested = null
    }
}
