package app.capnative.pdfviewer

import java.net.URI
import java.net.URLDecoder

internal const val MAX_PDF_BYTES: Long = 200L * 1024L * 1024L

internal object PdfError {
    const val INVALID_SOURCE = "INVALID_SOURCE"
    const val INVALID_PAGE = "INVALID_PAGE"
    const val INVALID_HEADERS = "INVALID_HEADERS"
    const val LOAD_ERROR = "LOAD_ERROR"
    const val PASSWORD_PROTECTED = "PASSWORD_PROTECTED"
    const val ALREADY_OPEN = "ALREADY_OPEN"
    const val CANCELLED = "CANCELLED"
    const val UNAVAILABLE = "UNAVAILABLE"
}

internal class PdfRequestException(
    val code: String,
    message: String,
) : Exception(message)

internal sealed class PdfSource {
    data class Remote(val url: String) : PdfSource()

    data class LocalFile(val path: String) : PdfSource()

    data class Content(val uri: String) : PdfSource()
}

internal data class PdfOpenRequest(
    val source: PdfSource,
    val title: String?,
    val page: Int,
    val headers: Map<String, String>,
) {
    val displayTitle: String
        get() {
            title?.let { return it }
            val raw = when (val current = source) {
                is PdfSource.Remote -> current.url
                is PdfSource.LocalFile -> current.path
                is PdfSource.Content -> current.uri
            }
            val segment = raw.substringBefore('?').substringBefore('#').substringAfterLast('/')
            if (segment.isEmpty()) {
                return "PDF"
            }
            return try {
                URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8.name())
            } catch (_: Exception) {
                segment
            }
        }
}

/**
 * One parser per source type. Each parser accepts only that type.
 * Web-only types validate their input, then reject with UNAVAILABLE.
 */
internal object PdfRequests {
    private const val CAPACITOR_FILE_MARKER = "/_capacitor_file_"
    private val headerName = Regex("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$")
    private val schemePattern = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

    fun parseUrl(
        url: Any?,
        title: Any?,
        page: Any?,
        headers: Map<String, Any?>?,
    ): PdfOpenRequest {
        val value = readString(url, "url")
        if (value.contains(CAPACITOR_FILE_MARKER) || !isHttpUrl(value)) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "url must be an http: or https: URL.")
        }
        return request(PdfSource.Remote(value), title, page, readHeaders(headers))
    }

    fun parseFile(url: Any?, title: Any?, page: Any?): PdfOpenRequest {
        val value = readString(url, "url")
        if (scheme(value) != "file") {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "url must be a file:// URI.")
        }
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "The file:// URI could not be parsed.")
        }
        val path = uri.path
        if (path.isNullOrEmpty()) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "The file:// URI could not be parsed.")
        }
        return request(PdfSource.LocalFile(path), title, page, emptyMap())
    }

    fun parseCapacitorFile(url: Any?, title: Any?, page: Any?): PdfOpenRequest {
        val value = readString(url, "url")
        val markerIndex = value.indexOf(CAPACITOR_FILE_MARKER)
        if (markerIndex < 0) {
            throw PdfRequestException(
                PdfError.INVALID_SOURCE,
                "url must be a Capacitor file URL containing _capacitor_file_.",
            )
        }
        val rawPath = value.substring(markerIndex + CAPACITOR_FILE_MARKER.length)
        val path = decodePath(rawPath.substringBefore('?').substringBefore('#'))
        if (!path.startsWith("/")) {
            throw PdfRequestException(
                PdfError.INVALID_SOURCE,
                "The Capacitor file URL did not contain an absolute path.",
            )
        }
        return request(PdfSource.LocalFile(path), title, page, emptyMap())
    }

    fun parsePath(path: Any?, title: Any?, page: Any?): PdfOpenRequest {
        val value = readString(path, "path")
        if (!value.startsWith("/") || value.startsWith("//")) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "path must be an absolute filesystem path.")
        }
        return request(PdfSource.LocalFile(value), title, page, emptyMap())
    }

    fun parseContent(uri: Any?, title: Any?, page: Any?): PdfOpenRequest {
        val value = readString(uri, "uri")
        if (scheme(value) != "content") {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "uri must be a content:// URI.")
        }
        return request(PdfSource.Content(value), title, page, emptyMap())
    }

    fun parseWebPath(path: Any?, title: Any?, page: Any?): PdfOpenRequest {
        val value = readString(path, "path")
        if (!isWebPath(value)) {
            throw PdfRequestException(
                PdfError.INVALID_SOURCE,
                "path must be a root-relative (/files/a.pdf) or dot-relative (./a.pdf) URL.",
            )
        }
        readTitle(title)
        readPage(page)
        throw PdfRequestException(PdfError.UNAVAILABLE, "Web paths are only supported on the web.")
    }

    fun parseBlob(url: Any?, title: Any?, page: Any?): PdfOpenRequest {
        val value = readString(url, "url")
        if (scheme(value) != "blob") {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "url must be a blob: URL.")
        }
        readTitle(title)
        readPage(page)
        throw PdfRequestException(PdfError.UNAVAILABLE, "blob: URLs are only supported on the web.")
    }

    fun parseData(url: Any?, title: Any?, page: Any?): PdfOpenRequest {
        val value = readString(url, "url")
        if (scheme(value) != "data") {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "url must be a data: URL.")
        }
        readTitle(title)
        readPage(page)
        throw PdfRequestException(PdfError.UNAVAILABLE, "data: URLs are only supported on the web.")
    }

    private fun request(
        source: PdfSource,
        title: Any?,
        page: Any?,
        headers: Map<String, String>,
    ): PdfOpenRequest {
        return PdfOpenRequest(
            source = source,
            title = readTitle(title),
            page = readPage(page),
            headers = headers,
        )
    }

    private fun readString(value: Any?, name: String): String {
        val text = (value as? String)?.trim()
            ?: throw PdfRequestException(PdfError.INVALID_SOURCE, "$name is required and must be a string.")
        if (text.isEmpty()) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "$name must not be empty.")
        }
        return text
    }

    private fun readTitle(value: Any?): String? {
        if (value == null) {
            return null
        }
        val title = value as? String
            ?: throw PdfRequestException(PdfError.INVALID_SOURCE, "title must be a string.")
        val trimmed = title.trim()
        return trimmed.ifEmpty { null }
    }

    private fun readPage(value: Any?): Int {
        if (value == null) {
            return 1
        }
        val page = when (value) {
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Float -> value.toDouble()
            is Double -> value
            else -> throw PdfRequestException(
                PdfError.INVALID_PAGE,
                "page must be an integer greater than or equal to 1.",
            )
        }
        if (page < 1.0 || page > Int.MAX_VALUE.toDouble() || page % 1.0 != 0.0) {
            throw PdfRequestException(
                PdfError.INVALID_PAGE,
                "page must be an integer greater than or equal to 1.",
            )
        }
        return page.toInt()
    }

    private fun readHeaders(value: Map<String, Any?>?): Map<String, String> {
        if (value == null) {
            return emptyMap()
        }
        val headers = linkedMapOf<String, String>()
        for ((name, headerValue) in value) {
            val stringValue = headerValue as? String
                ?: throw PdfRequestException(PdfError.INVALID_HEADERS, "Header values must be strings.")
            if (!headerName.matches(name) || stringValue.contains('\r') || stringValue.contains('\n')) {
                throw PdfRequestException(
                    PdfError.INVALID_HEADERS,
                    "Header names and values must not be empty or contain newlines.",
                )
            }
            headers[name] = stringValue
        }
        return headers
    }

    private fun scheme(value: String): String? {
        return schemePattern.find(value)?.groupValues?.get(1)?.lowercase()
    }

    private fun isHttpUrl(value: String): Boolean {
        val scheme = scheme(value)
        return scheme == "http" || scheme == "https"
    }

    private fun isWebPath(value: String): Boolean {
        if (scheme(value) != null) {
            return false
        }
        return (value.startsWith("/") && !value.startsWith("//")) ||
            value.startsWith("./") ||
            value.startsWith("../")
    }

    private fun decodePath(value: String): String {
        return try {
            URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }
}
