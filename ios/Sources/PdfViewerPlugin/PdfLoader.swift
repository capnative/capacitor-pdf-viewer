import Foundation

struct LoadedPdf {
    let fileURL: URL
    let ownsFile: Bool
}

enum PdfLoader {
    static let maxBytes: Int64 = 200 * 1024 * 1024

    static func load(_ request: PdfOpenRequest) async throws -> LoadedPdf {
        switch request.source {
        case .localFile(let url):
            guard FileManager.default.isReadableFile(atPath: url.path) else {
                throw PdfViewerError.loadFailed("The PDF file could not be read.")
            }
            return LoadedPdf(fileURL: url, ownsFile: false)
        case .remote(let url):
            return try await download(url, headers: request.headers)
        case .asset:
            throw PdfViewerError.loadFailed("The PDF asset could not be read.")
        }
    }

    private static func download(_ url: URL, headers: [String: String]) async throws -> LoadedPdf {
        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 60
        request.setValue("application/pdf,*/*", forHTTPHeaderField: "Accept")
        for (name, value) in headers {
            request.setValue(value, forHTTPHeaderField: name)
        }

        let (tempURL, response) = try await URLSession.shared.download(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw PdfViewerError.loadFailed("The PDF could not be downloaded.")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw PdfViewerError.loadFailed("The PDF could not be downloaded (HTTP \(http.statusCode)).")
        }
        if let length = http.value(forHTTPHeaderField: "Content-Length"),
           let bytes = Int64(length),
           bytes > maxBytes {
            throw PdfViewerError.loadFailed("The PDF is larger than 200 MB.")
        }

        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent("capnative-pdf-\(UUID().uuidString).pdf")
        do {
            try FileManager.default.moveItem(at: tempURL, to: destination)
        } catch {
            try FileManager.default.copyItem(at: tempURL, to: destination)
            try? FileManager.default.removeItem(at: tempURL)
        }

        let attributes = try? FileManager.default.attributesOfItem(atPath: destination.path)
        let size = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
        if size == 0 {
            try? FileManager.default.removeItem(at: destination)
            throw PdfViewerError.loadFailed("The PDF download was empty.")
        }
        if size > maxBytes {
            try? FileManager.default.removeItem(at: destination)
            throw PdfViewerError.loadFailed("The PDF is larger than 200 MB.")
        }
        try Task.checkCancellation()
        return LoadedPdf(fileURL: destination, ownsFile: true)
    }
}
