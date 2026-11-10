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

    static func unavailable(_ message: String) -> PdfViewerError {
        PdfViewerError(code: "UNAVAILABLE", message: message)
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

    static func parseUrl(
        url rawURL: Any?,
        title rawTitle: Any?,
        page rawPage: Any?,
        headers rawHeaders: Any?
    ) throws -> PdfOpenRequest {
        let url = try readString(rawURL, name: "url")
        if url.contains(capacitorFileMarker) || !isHttpURL(url) {
            throw PdfViewerError.invalidSource("url must be an http: or https: URL.")
        }
        guard let parsed = URL(string: url) else {
            throw PdfViewerError.invalidSource("url must be an http: or https: URL.")
        }
        return try make(
            source: .remote(parsed),
            title: rawTitle,
            page: rawPage,
            headers: try readHeaders(rawHeaders)
        )
    }

    static func parseFile(url rawURL: Any?, title rawTitle: Any?, page rawPage: Any?) throws -> PdfOpenRequest {
        let url = try readString(rawURL, name: "url")
        guard schemeOf(url) == "file", let parsed = URL(string: url) else {
            throw PdfViewerError.invalidSource("url must be a file:// URI.")
        }
        if parsed.path.isEmpty {
            throw PdfViewerError.invalidSource("The file:// URI could not be parsed.")
        }
        return try make(
            source: .localFile(URL(fileURLWithPath: parsed.path)),
            title: rawTitle,
            page: rawPage,
            headers: [:]
        )
    }

    static func parseCapacitorFile(
        url rawURL: Any?,
        title rawTitle: Any?,
        page rawPage: Any?
    ) throws -> PdfOpenRequest {
        let url = try readString(rawURL, name: "url")
        guard let marker = url.range(of: capacitorFileMarker) else {
            throw PdfViewerError.invalidSource("url must be a Capacitor file URL containing _capacitor_file_.")
        }
        let rawPath = String(url[marker.upperBound...])
        let cut = rawPath.components(separatedBy: "?").first?.components(separatedBy: "#").first ?? rawPath
        let path = cut.removingPercentEncoding ?? cut
        guard path.hasPrefix("/") else {
            throw PdfViewerError.invalidSource("The Capacitor file URL did not contain an absolute path.")
        }
        return try make(
            source: .localFile(URL(fileURLWithPath: path)),
            title: rawTitle,
            page: rawPage,
            headers: [:]
        )
    }

    static func parsePath(path rawPath: Any?, title rawTitle: Any?, page rawPage: Any?) throws -> PdfOpenRequest {
        let path = try readString(rawPath, name: "path")
        guard path.hasPrefix("/"), !path.hasPrefix("//") else {
            throw PdfViewerError.invalidSource("path must be an absolute filesystem path.")
        }
        return try make(
            source: .localFile(URL(fileURLWithPath: path)),
            title: rawTitle,
            page: rawPage,
            headers: [:]
        )
    }

    static func parseContent(uri rawURI: Any?, title rawTitle: Any?, page rawPage: Any?) throws -> PdfOpenRequest {
        let uri = try readString(rawURI, name: "uri")
        guard schemeOf(uri) == "content" else {
            throw PdfViewerError.invalidSource("uri must be a content:// URI.")
        }
        _ = try readTitle(rawTitle)
        _ = try readPage(rawPage)
        throw PdfViewerError.unavailable("content:// URIs are only supported on Android.")
    }

    private static func make(
        source: Source,
        title rawTitle: Any?,
        page rawPage: Any?,
        headers: [String: String]
    ) throws -> PdfOpenRequest {
        PdfOpenRequest(
            source: source,
            title: try readTitle(rawTitle),
            page: try readPage(rawPage),
            headers: headers
        )
    }
}

private let capacitorFileMarker = "/_capacitor_file_"
private let headerNamePattern = "^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$"
private let schemePattern = "^([a-zA-Z][a-zA-Z0-9+.-]*):"
private let pageMessage = "page must be an integer greater than or equal to 1."

private func readString(_ value: Any?, name: String) throws -> String {
    guard let text = (value as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) else {
        throw PdfViewerError.invalidSource("\(name) is required and must be a string.")
    }
    if text.isEmpty {
        throw PdfViewerError.invalidSource("\(name) must not be empty.")
    }
    return text
}

private func schemeOf(_ value: String) -> String? {
    guard let match = value.range(of: schemePattern, options: .regularExpression) else {
        return nil
    }
    return String(value[match].dropLast()).lowercased()
}

private func isHttpURL(_ value: String) -> Bool {
    let scheme = schemeOf(value)
    return scheme == "http" || scheme == "https"
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

