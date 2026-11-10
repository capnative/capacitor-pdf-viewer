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

    func testParsesEachLocalURI() throws {
        let file = try PdfOpenRequest.parseUri(
            uri: "file:///tmp/my%20file.pdf",
            title: nil,
            page: 2
        )
        XCTAssertEqual(file.source, .localFile(URL(fileURLWithPath: "/tmp/my file.pdf")))
        XCTAssertEqual(file.page, 2)

        let capacitor = try PdfOpenRequest.parseUri(
            uri: "capacitor://localhost/_capacitor_file_/var/mobile/a.pdf",
            title: "  ",
            page: nil
        )
        XCTAssertEqual(capacitor.source, .localFile(URL(fileURLWithPath: "/var/mobile/a.pdf")))
        XCTAssertNil(capacitor.title)
        XCTAssertEqual(capacitor.displayTitle, "a.pdf")

        let absolute = try PdfOpenRequest.parseUri(
            uri: "/var/mobile/Documents/report.pdf",
            title: nil,
            page: 4.0
        )
        XCTAssertEqual(absolute.source, .localFile(URL(fileURLWithPath: "/var/mobile/Documents/report.pdf")))
        XCTAssertEqual(absolute.page, 4)
    }

    func testParsesAnAssetPathInsideTheWebDirectory() throws {
        let asset = try PdfOpenRequest.parseAsset(path: " /docs/guide.pdf ", title: nil, page: 3)
        XCTAssertEqual(asset.source, .asset("docs/guide.pdf"))
        XCTAssertEqual(asset.page, 3)
        XCTAssertEqual(asset.displayTitle, "guide.pdf")

        let rooted = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
        let resolved = try PdfOpenRequest.resolveAsset(asset, root: rooted)
        let expected = rooted.standardizedFileURL.appendingPathComponent("docs/guide.pdf").standardizedFileURL
        XCTAssertEqual(resolved.source, .localFile(expected))
    }

    func testRejectsTheWrongSourceForEachParser() {
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUrl(url: "file:///tmp/a.pdf", title: nil, page: nil, headers: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(
            errorCode { try PdfOpenRequest.parseUrl(url: "https://localhost/_capacitor_file_/var/a.pdf", title: nil, page: nil, headers: nil) },
            "INVALID_SOURCE"
        )
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUri(uri: "https://example.com/a.pdf", title: nil, page: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUri(uri: "manual.pdf", title: nil, page: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseUri(uri: "content://com.example/docs/1", title: nil, page: nil) }, "UNAVAILABLE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseAsset(path: "../secret.pdf", title: nil, page: nil) }, "INVALID_SOURCE")
        XCTAssertEqual(errorCode { try PdfOpenRequest.parseAsset(path: "docs/../../etc/passwd", title: nil, page: nil) }, "INVALID_SOURCE")
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
