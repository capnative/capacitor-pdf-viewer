package app.capnative.pdfviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfRequestsTest {
    @Test
    fun parsesRemoteUrlAndDefaults() {
        val request = PdfRequests.parseUrl(
            url = "  https://example.com/files/manual.pdf  ",
            title = "  Guide  ",
            page = null,
            headers = mapOf("Authorization" to "Bearer token"),
        )

        assertEquals(PdfSource.Remote("https://example.com/files/manual.pdf"), request.source)
        assertEquals("Guide", request.title)
        assertEquals(1, request.page)
        assertEquals(mapOf("Authorization" to "Bearer token"), request.headers)
        assertEquals("Guide", request.displayTitle)
    }

    @Test
    fun parsesEachLocalUri() {
        assertEquals(
            PdfSource.LocalFile("/tmp/my file.pdf"),
            PdfRequests.parseUri("file:///tmp/my%20file.pdf", null, 2).source,
        )
        assertEquals(2, PdfRequests.parseUri("file:///tmp/my%20file.pdf", null, 2).page)
        assertEquals(
            PdfSource.LocalFile("/storage/emulated/0/a.pdf"),
            PdfRequests.parseUri(
                "https://localhost/_capacitor_file_/storage/emulated/0/a.pdf",
                null,
                null,
            ).source,
        )
        assertEquals(
            PdfSource.Content("content://com.example/docs/1"),
            PdfRequests.parseUri("content://com.example/docs/1", null, null).source,
        )
        assertEquals(
            PdfSource.LocalFile("/sdcard/Download/a.pdf"),
            PdfRequests.parseUri("/sdcard/Download/a.pdf", null, null).source,
        )
    }

    @Test
    fun parsesAnAssetPathInsideTheWebDirectory() {
        assertEquals(PdfSource.Asset("docs/guide.pdf"), PdfRequests.parseAsset(" /docs/guide.pdf ", null, 3).source)
        assertEquals(3, PdfRequests.parseAsset("./files/manual.pdf", null, 3).page)
        assertEquals("guide.pdf", PdfRequests.parseAsset("docs/guide.pdf", "  ", null).displayTitle)
    }

    @Test
    fun usesDecodedFileNameWhenTitleIsBlank() {
        val request = PdfRequests.parseUrl("https://cdn.example.com/Spec%20v1.pdf?download=1", "  ", null, null)
        assertNull(request.title)
        assertEquals("Spec v1.pdf", request.displayTitle)
    }

    @Test
    fun rejectsTheWrongSourceForEachParser() {
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseUrl("file:///tmp/a.pdf", null, null, null) })
        assertEquals(
            PdfError.INVALID_SOURCE,
            errorCode { PdfRequests.parseUrl("https://localhost/_capacitor_file_/a.pdf", null, null, null) },
        )
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseUri("https://example.com/a.pdf", null, null) })
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseUri("manual.pdf", null, null) })
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseAsset("../secret.pdf", null, null) })
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseAsset("docs/../../etc/passwd", null, null) })
    }

    @Test
    fun rejectsEmptyAndUnknownSources() {
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseUrl("   ", null, null, null) })
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseUrl("manual.pdf", null, null, null) })
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseUrl("javascript:alert(1)", null, null, null) })
        assertEquals(PdfError.INVALID_SOURCE, errorCode { PdfRequests.parseUrl(12, null, null, null) })
    }

    @Test
    fun rejectsPagesThatAreNotPositiveIntegers() {
        assertEquals(PdfError.INVALID_PAGE, errorCode { PdfRequests.parseUrl("https://example.com/a.pdf", null, 0, null) })
        assertEquals(PdfError.INVALID_PAGE, errorCode { PdfRequests.parseUrl("https://example.com/a.pdf", null, 1.5, null) })
        assertEquals(PdfError.INVALID_PAGE, errorCode { PdfRequests.parseUrl("https://example.com/a.pdf", null, -3, null) })
        assertEquals(PdfError.INVALID_PAGE, errorCode { PdfRequests.parseUrl("https://example.com/a.pdf", null, true, null) })
        assertEquals(4, PdfRequests.parseUrl("https://example.com/a.pdf", null, 4.0, null).page)
    }

    @Test
    fun rejectsUnsafeHeaders() {
        assertEquals(
            PdfError.INVALID_HEADERS,
            errorCode { PdfRequests.parseUrl("https://example.com/a.pdf", null, null, mapOf("Authorization" to "Bearer\r\nX: 1")) },
        )
        assertEquals(
            PdfError.INVALID_HEADERS,
            errorCode { PdfRequests.parseUrl("https://example.com/a.pdf", null, null, mapOf("Not A Header" to "value")) },
        )
        assertEquals(
            PdfError.INVALID_HEADERS,
            errorCode { PdfRequests.parseUrl("https://example.com/a.pdf", null, null, mapOf("Accept" to 1)) },
        )
    }

    private fun errorCode(block: () -> Unit): String {
        val error = assertThrows(PdfRequestException::class.java, block)
        return error.code
    }
}
