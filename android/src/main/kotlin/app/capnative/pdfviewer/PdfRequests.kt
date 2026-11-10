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

    data class Asset(val path: String) : PdfSource()
}

internal data class PdfOpenRequest(
    val source: PdfSource,
    val title: String?,
    val page: Int,
    val headers: Map<String, String>,
    val assetRoot: String? = null,
) {
    val displayTitle: String
        get() {
            title?.let { return it }
            val raw = when (val current = source) {
                is PdfSource.Remote -> current.url
                is PdfSource.LocalFile -> current.path
                is PdfSource.Content -> current.uri
                is PdfSource.Asset -> current.path
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
 */
internal object PdfRequests {
    private const val CAPACITOR_FILE_MARKER = "/_capacitor_file_"
    private const val URI_MESSAGE =
        "uri must be a file:// URI, a content:// URI, a Capacitor file URL, or an absolute filesystem path."
    private const val ASSET_MESSAGE = "path must be a relative path inside the app assets."
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

    fun parseUri(uri: Any?, title: Any?, page: Any?): PdfOpenRequest {
        val value = readString(uri, "uri")
        val markerIndex = value.indexOf(CAPACITOR_FILE_MARKER)
        if (markerIndex >= 0) {
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
        when (scheme(value)) {
            "file" -> {
                val parsed = try {
                    URI(value)
                } catch (_: Exception) {
                    throw PdfRequestException(PdfError.INVALID_SOURCE, "The file:// URI could not be parsed.")
                }
                val path = parsed.path
                if (path.isNullOrEmpty()) {
                    throw PdfRequestException(PdfError.INVALID_SOURCE, "The file:// URI could not be parsed.")
                }
                return request(PdfSource.LocalFile(path), title, page, emptyMap())
            }
            "content" -> return request(PdfSource.Content(value), title, page, emptyMap())
            null -> {
                if (value.startsWith("/") && !value.startsWith("//")) {
                    return request(PdfSource.LocalFile(value), title, page, emptyMap())
                }
            }
        }
        throw PdfRequestException(PdfError.INVALID_SOURCE, URI_MESSAGE)
    }

    fun parseAsset(path: Any?, title: Any?, page: Any?): PdfOpenRequest {
        return request(PdfSource.Asset(normalizeAssetPath(readString(path, "path"))), title, page, emptyMap())
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

    private fun normalizeAssetPath(value: String): String {
        if (scheme(value) != null || value.contains('\\') || value.contains('\u0000')) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, ASSET_MESSAGE)
        }
        var path = value
        if (path.startsWith("/")) {
            if (path.startsWith("//")) {
                throw PdfRequestException(PdfError.INVALID_SOURCE, ASSET_MESSAGE)
            }
            path = path.substring(1)
        }
        while (path.startsWith("./")) {
            path = path.substring(2)
        }
        if (path.isEmpty()) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, ASSET_MESSAGE)
        }
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, ASSET_MESSAGE)
        }
        return parts.joinToString("/")
    }

    private fun decodePath(value: String): String {
        return try {
            URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }
}
