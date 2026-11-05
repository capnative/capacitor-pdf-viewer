import Foundation

struct PdfViewerError: Error, Equatable {
    let code: String
    let message: String

    static func invalidSource(_ message: String) -> PdfViewerError {
        PdfViewerError(code: "INVALID_SOURCE", message: message)
    }

    static func invalidPage(_ message: String) -> PdfViewerError {
        PdfViewerError(
            code: "INVALID_PAGE",
            message: message
        )
    }

    static func invalidHeaders(_ message: String) -> PdfViewerError {
        PdfViewerError(code: "INVALID_HEADERS", message: message)
    }

    static func loadFailed(_ message: String) -> PdfViewerError {
        PdfViewerError(code: "LOAD_ERROR", message: message)
    }
}

struct PdfOpenRequest: Equatable {
    enum Source: Equatable {
        case remote(URL)
        case localFile(URL)
    }

    let source: Source
    let title: String?
    let page: Int
    let headers: [String: String]

    var displayTitle: String {
        if let title, !title.isEmpty {
            return title
        }
        let url: URL
        switch source {
        case .remote(let remote):
            url = remote
        case .localFile(let local):
            url = local
        }
        let name = url.lastPathComponent
        return name.isEmpty ? "PDF" : name
    }

    static func parse(
        url rawURL: Any?,
        title rawTitle: Any?,
        page rawPage: Any?,
        headers rawHeaders: Any?
    ) throws -> PdfOpenRequest {
        let url = try readURL(rawURL)
        return PdfOpenRequest(
            source: try classify(url),
            title: try readTitle(rawTitle),
            page: try readPage(rawPage),
            headers: try readHeaders(rawHeaders)
        )
    }
}

private let capacitorFileMarker = "/_capacitor_file_"
private let headerNamePattern = "^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$"
private let pageMessage = "page must be an integer greater than or equal to 1."

private func readURL(_ value: Any?) throws -> String {
    guard let url = (value as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) else {
        throw PdfViewerError.invalidSource("url is required and must be a string.")
    }
    if url.isEmpty {
        throw PdfViewerError.invalidSource("url must not be empty.")
    }
    return url
}

private func readTitle(_ value: Any?) throws -> String? {
    if value == nil || value is NSNull {
        return nil
    }
    guard let title = value as? String else {
        throw PdfViewerError.invalidSource("title must be a string.")
    }
    let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? nil : trimmed
}

private func readPage(_ value: Any?) throws -> Int {
    if value == nil || value is NSNull {
        return 1
    }
    if value is Bool {
        throw PdfViewerError.invalidPage(pageMessage)
    }
    let number: Double
    if let intValue = value as? Int {
        number = Double(intValue)
    } else if let doubleValue = value as? Double {
        number = doubleValue
    } else if let floatValue = value as? Float {
        number = Double(floatValue)
    } else if let numeric = value as? NSNumber {
        number = numeric.doubleValue
    } else {
        throw PdfViewerError.invalidPage(pageMessage)
    }
    guard number >= 1, number <= Double(Int.max), number == floor(number) else {
        throw PdfViewerError.invalidPage(pageMessage)
    }
    return Int(number)
}

private func readHeaders(_ value: Any?) throws -> [String: String] {
    if value == nil || value is NSNull {
        return [:]
    }
    let entries: [(String, Any)]
    if let object = value as? [String: Any] {
        entries = object.map { ($0.key, $0.value) }
    } else if let object = value as? NSDictionary {
        entries = object.allKeys.compactMap { key in
            guard let name = key as? String else {
                return nil
            }
            return (name, object[key] as Any)
        }
    } else {
        throw PdfViewerError.invalidHeaders("headers must be an object whose values are strings.")
    }

    var headers: [String: String] = [:]
    for (name, headerValue) in entries {
        guard let stringValue = headerValue as? String else {
            throw PdfViewerError.invalidHeaders("Header values must be strings.")
        }
        let nameIsToken = name.range(of: headerNamePattern, options: .regularExpression) != nil
        let valueIsSafe = !stringValue.contains("\r") && !stringValue.contains("\n")
        if !nameIsToken || !valueIsSafe {
            throw PdfViewerError.invalidHeaders(
                "Header names and values must not be empty or contain newlines."
            )
        }
        headers[name] = stringValue
    }
    return headers
}

private func classify(_ url: String) throws -> PdfOpenRequest.Source {
    if let marker = url.range(of: capacitorFileMarker) {
        let rawPath = String(url[marker.upperBound...])
        let cut = rawPath.components(separatedBy: "?").first?.components(separatedBy: "#").first ?? rawPath
        let path = cut.removingPercentEncoding ?? cut
        guard path.hasPrefix("/") else {
            throw PdfViewerError.invalidSource("The Capacitor file URL did not contain an absolute path.")
        }
        return .localFile(URL(fileURLWithPath: path))
    }

    if url.hasPrefix("/"), !url.hasPrefix("//") {
        return .localFile(URL(fileURLWithPath: url))
    }
    if url.hasPrefix("./") || url.hasPrefix("../") {
        throw PdfViewerError.invalidSource("Relative URLs are only supported on the web.")
    }

    guard let parsed = URL(string: url), let scheme = parsed.scheme?.lowercased() else {
        throw PdfViewerError.invalidSource(
            "url must be an http(s) URL, a file:// URI, or an absolute file path."
        )
    }

    switch scheme {
    case "http", "https":
        return .remote(parsed)
    case "file":
        if parsed.path.isEmpty {
            throw PdfViewerError.invalidSource("The file:// URI could not be parsed.")
        }
        return .localFile(URL(fileURLWithPath: parsed.path))
    case "content":
        throw PdfViewerError.invalidSource("content:// URIs are only supported on Android.")
    case "blob", "data":
        throw PdfViewerError.invalidSource("blob: and data: URLs are only supported on the web.")
    default:
        throw PdfViewerError.invalidSource("The URL scheme \"\(scheme):\" is not supported.")
    }
}
