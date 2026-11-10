import XCTest
@testable import PdfViewerPlugin

final class PdfRequestTests: XCTestCase {
    func testParsesRemoteURLAndDefaults() throws {
        let request = try PdfOpenRequest.parseUrl(
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

    func testParsesEachLocalSourceOnItsOwnParser() throws {
        let file = try PdfOpenRequest.parseFile(
            url: "file:///tmp/my%20file.pdf",
            title: nil,
            page: 2
        )
        XCTAssertEqual(file.source, .localFile(URL(fileURLWithPath: "/tmp/my file.pdf")))
        XCTAssertEqual(file.page, 2)

        let capacitor = try PdfOpenRequest.parseCapacitorFile(
            url: "capacitor://localhost/_capacitor_file_/var/mobile/a.pdf",
            title: "  ",
            page: nil
        )
        XCTAssertEqual(capacitor.source, .localFile(URL(fileURLWithPath: "/var/mobile/a.pdf")))
        XCTAssertNil(capacitor.title)
        XCTAssertEqual(capacitor.displayTitle, "a.pdf")

        let absolute = try PdfOpenRequest.parsePath(
            path: "/var/mobile/Documents/report.pdf",
            title: nil,
            page: 4.0
        )
        XCTAssertEqual(absolute.source, .localFile(URL(fileURLWithPath: "/var/mobile/Documents/report.pdf")))
        XCTAssertEqual(absolute.page, 4)
    }

    func testRejectsTheWrongSourceForEachParser() {
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUrl(url: "file:///tmp/a.pdf", title: nil, page: nil, headers: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(
            errorCode { try PdfOpenRequest.parseUrl(url: "https://localhost/_capacitor_file_/var/a.pdf", title: nil, page: nil, headers: nil) },
            "INVALID_SOURCE"
        )
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseFile(url: "https://example.com/a.pdf", title: nil, page: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parsePath(path: "./manual.pdf", title: nil, page: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseContent(uri: "file:///tmp/a.pdf", title: nil, page: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseContent(uri: "content://com.example/docs/1", title: nil, page: nil) }, "UNAVAILABLE")
    }

    func testRejectsUnsupportedSources() {
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUrl(url: "   ", title: nil, page: nil, headers: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUrl(url: "manual.pdf", title: nil, page: nil, headers: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUrl(url: "javascript:alert(1)", title: nil, page: nil, headers: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUrl(url: 12, title: nil, page: nil, headers: nil) }, "INVALID_SOURCE")
    }

    func testRejectsPagesAndHeaders() {
        XCTAssertEqual(
            errorCode { try PdfOpenRequest.parseUrl(url: "https://example.com/a.pdf", title: nil, page: 0, headers: nil) },
            "INVALID_PAGE"
        )
        XCTAssertEqual(
            errorCode { try PdfOpenRequest.parseUrl(url: "https://example.com/a.pdf", title: nil, page: 1.5, headers: nil) },
            "INVALID_PAGE"
        )
        XCTAssertEqual(
            errorCode { try PdfOpenRequest.parseUrl(url: "https://example.com/a.pdf", title: nil, page: true, headers: nil) },
            "INVALID_PAGE"
        )
        XCTAssertEqual(
            errorCode {
                try PdfOpenRequest.parseUrl(
                    url: "https://example.com/a.pdf",
                    title: nil,
                    page: nil,
                    headers: ["Authorization": "Bearer\r\nX: 1"]
                )
            },
            "INVALID_HEADERS"
        )
        XCTAssertEqual(
            errorCode { try PdfOpenRequest.parseUrl(url: "https://example.com/a.pdf", title: nil, page: nil, headers: ["Not A Header": "value"]) },
            "INVALID_HEADERS"
        )
        XCTAssertEqual(
            errorCode { try PdfOpenRequest.parseUrl(url: "https://example.com/a.pdf", title: nil, page: nil, headers: ["Accept": 1]) },
            "INVALID_HEADERS"
        )
    }

    private func errorCode(_ run: () throws -> Void) -> String? {
        do {
            try run()
            return nil
        } catch let error as PdfViewerError {
            return error.code
        } catch {
            return nil
        }
    }
}
