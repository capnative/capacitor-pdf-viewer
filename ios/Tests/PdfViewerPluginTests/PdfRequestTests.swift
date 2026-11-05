import XCTest
@testable import PdfViewerPlugin

final class PdfRequestTests: XCTestCase {
    func testParsesRemoteURLAndDefaults() throws {
        let request = try PdfOpenRequest.parse(
            url: "  https://example.com/files/manual.pdf  ",
            title: "  Guide  ",
            page: nil,
            headers: ["Authorization": "Bearer token"]
        )

        let expected = try XCTUnwrap(URL(string: "https://example.com/files/manual.pdf"))
        XCTAssertEqual(request.source, .remote(expected))
        XCTAssertEqual(request.title, "Guide")
        XCTAssertEqual(request.page, 1)
        XCTAssertEqual(request.headers["Authorization"], "Bearer token")
        XCTAssertEqual(request.displayTitle, "Guide")
    }

    func testParsesFileAndCapacitorURLs() throws {
        let file = try PdfOpenRequest.parse(
            url: "file:///tmp/my%20file.pdf",
            title: nil,
            page: 2,
            headers: nil
        )
        XCTAssertEqual(file.source, .localFile(URL(fileURLWithPath: "/tmp/my file.pdf")))
        XCTAssertEqual(file.page, 2)

        let capacitor = try PdfOpenRequest.parse(
            url: "capacitor://localhost/_capacitor_file_/var/mobile/a.pdf",
            title: "  ",
            page: nil,
            headers: nil
        )
        XCTAssertEqual(capacitor.source, .localFile(URL(fileURLWithPath: "/var/mobile/a.pdf")))
        XCTAssertNil(capacitor.title)
        XCTAssertEqual(capacitor.displayTitle, "a.pdf")

        let absolute = try PdfOpenRequest.parse(
            url: "/var/mobile/Documents/report.pdf",
            title: nil,
            page: 4.0,
            headers: nil
        )
        XCTAssertEqual(absolute.source, .localFile(URL(fileURLWithPath: "/var/mobile/Documents/report.pdf")))
        XCTAssertEqual(absolute.page, 4)
    }

    func testRejectsUnsupportedSources() {
        XCTAssertEqual(errorCode(url: "   "), "INVALID_SOURCE")
        XCTAssertEqual(errorCode(url: "./manual.pdf"), "INVALID_SOURCE")
        XCTAssertEqual(errorCode(url: "manual.pdf"), "INVALID_SOURCE")
        XCTAssertEqual(errorCode(url: "content://com.example/docs/1"), "INVALID_SOURCE")
        XCTAssertEqual(errorCode(url: "blob:https://localhost/uuid"), "INVALID_SOURCE")
        XCTAssertEqual(errorCode(url: "javascript:alert(1)"), "INVALID_SOURCE")
        XCTAssertEqual(errorCode(url: 12), "INVALID_SOURCE")
    }

    func testRejectsPagesAndHeaders() {
        XCTAssertEqual(errorCode(url: "https://example.com/a.pdf", page: 0), "INVALID_PAGE")
        XCTAssertEqual(errorCode(url: "https://example.com/a.pdf", page: 1.5), "INVALID_PAGE")
        XCTAssertEqual(errorCode(url: "https://example.com/a.pdf", page: true), "INVALID_PAGE")
        XCTAssertEqual(
            errorCode(url: "https://example.com/a.pdf", headers: ["Authorization": "Bearer\r\nX: 1"]),
            "INVALID_HEADERS"
        )
        XCTAssertEqual(
            errorCode(url: "https://example.com/a.pdf", headers: ["Not A Header": "value"]),
            "INVALID_HEADERS"
        )
        XCTAssertEqual(
            errorCode(url: "https://example.com/a.pdf", headers: ["Accept": 1]),
            "INVALID_HEADERS"
        )
    }

    private func errorCode(
        url: Any?,
        title: Any? = nil,
        page: Any? = nil,
        headers: Any? = nil
    ) -> String? {
        do {
            _ = try PdfOpenRequest.parse(url: url, title: title, page: page, headers: headers)
            return nil
        } catch let error as PdfViewerError {
            return error.code
        } catch {
            return nil
        }
    }
}
