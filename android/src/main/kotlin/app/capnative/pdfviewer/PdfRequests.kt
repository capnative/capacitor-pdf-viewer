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
 * Native source rules. A leading slash is a filesystem path. Web-relative
 * URLs are rejected here; the web implementation accepts them.
 */
internal object PdfRequests {
    private const val CAPACITOR_FILE_MARKER = "/_capacitor_file_"
    private val headerName = Regex("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$")

    fun parse(
        url: Any?,
        title: Any?,
        page: Any?,
        headers: Map<String, Any?>?,
    ): PdfOpenRequest {
        return PdfOpenRequest(
            source = classify(readUrl(url)),
            title = readTitle(title),
            page = readPage(page),
            headers = readHeaders(headers),
        )
    }

    private fun readUrl(value: Any?): String {
        val url = (value as? String)?.trim()
            ?: throw PdfRequestException(PdfError.INVALID_SOURCE, "url is required and must be a string.")
        if (url.isEmpty()) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "url must not be empty.")
        }
        return url
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

    private fun classify(url: String): PdfSource {
        val markerIndex = url.indexOf(CAPACITOR_FILE_MARKER)
        if (markerIndex >= 0) {
            val rawPath = url.substring(markerIndex + CAPACITOR_FILE_MARKER.length)
            val path = decodePath(rawPath.substringBefore('?').substringBefore('#'))
            if (!path.startsWith("/")) {
                throw PdfRequestException(
                    PdfError.INVALID_SOURCE,
                    "The Capacitor file URL did not contain an absolute path.",
                )
            }
            return PdfSource.LocalFile(path)
        }

        if (url.startsWith("/") && !url.startsWith("//")) {
            return PdfSource.LocalFile(url)
        }
        if (url.startsWith("./") || url.startsWith("../")) {
            throw PdfRequestException(
                PdfError.INVALID_SOURCE,
                "Relative URLs are only supported on the web.",
            )
        }

        val uri = try {
            URI(url)
        } catch (_: Exception) {
            throw PdfRequestException(PdfError.INVALID_SOURCE, "url is not a valid URL or file path.")
        }
        when (uri.scheme?.lowercase()) {
            "http", "https" -> return PdfSource.Remote(url)
            "file" -> {
                val path = uri.path
                if (path.isNullOrEmpty()) {
                    throw PdfRequestException(PdfError.INVALID_SOURCE, "The file:// URI could not be parsed.")
                }
                return PdfSource.LocalFile(path)
            }
            "content" -> return PdfSource.Content(url)
            "blob", "data" -> throw PdfRequestException(
                PdfError.INVALID_SOURCE,
                "blob: and data: URLs are only supported on the web.",
            )
            null -> throw PdfRequestException(
                PdfError.INVALID_SOURCE,
                "url must be an http(s) URL, a file:// URI, a content:// URI, or an absolute file path.",
            )
            else -> throw PdfRequestException(
                PdfError.INVALID_SOURCE,
                "The URL scheme \"${uri.scheme}:\" is not supported.",
            )
        }
    }

    private fun decodePath(value: String): String {
        return try {
            URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }
}
