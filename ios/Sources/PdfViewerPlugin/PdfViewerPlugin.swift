import Capacitor
import UIKit

@objc(PdfViewerPlugin)
public class PdfViewerPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "PdfViewerPlugin"
    public let jsName = "PdfViewer"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "open", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "close", returnType: CAPPluginReturnPromise),
    ]

    private var viewer: PdfViewerViewController?
    private var pendingCall: CAPPluginCall?

    @objc func open(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                call.reject("The viewer could not be presented.", "UNAVAILABLE")
                return
            }
            if self.viewer != nil {
                call.reject("A PDF viewer is already open. Call close() first.", "ALREADY_OPEN")
                return
            }
            do {
                let request = try PdfOpenRequest.parse(
                    url: call.getValue("url"),
                    title: call.getValue("title"),
                    page: call.getValue("page"),
                    headers: try self.headerDictionary(from: call)
                )
                guard let presenter = self.topViewController(from: self.bridge?.viewController) else {
                    call.reject("The viewer could not be presented.", "UNAVAILABLE")
                    return
                }
                let viewer = PdfViewerViewController(request: request)
                viewer.onOpened = { [weak self] in
                    self?.pendingCall?.resolve()
                    self?.pendingCall = nil
                }
                viewer.onFailed = { [weak self] error in
                    self?.pendingCall?.reject(error.message, error.code)
                    self?.pendingCall = nil
                    self?.viewer = nil
                }
                viewer.onPageChanged = { [weak self] page, pageCount in
                    self?.notifyListeners("pageChanged", data: [
                        "page": page,
                        "pageCount": pageCount,
                    ])
                }
                viewer.onClosed = { [weak self] page, pageCount in
                    self?.notifyListeners("closed", data: [
                        "page": page,
                        "pageCount": pageCount,
                    ])
                    self?.viewer = nil
                }
                self.viewer = viewer
                self.pendingCall = call
                let navigation = UINavigationController(rootViewController: viewer)
                navigation.modalPresentationStyle = .fullScreen
                presenter.present(navigation, animated: true)
            } catch let error as PdfViewerError {
                call.reject(error.message, error.code)
            } catch {
                call.reject("The PDF could not be opened.", "LOAD_ERROR")
            }
        }
    }

    @objc func close(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.viewer?.dismiss(animated: true)
            call.resolve()
        }
    }

    private func headerDictionary(from call: CAPPluginCall) throws -> [String: Any]? {
        guard let raw = call.getValue("headers") else {
            return nil
        }
        if raw is NSNull {
            return nil
        }
        guard let object = raw as? JSObject else {
            throw PdfViewerError.invalidHeaders("headers must be an object whose values are strings.")
        }
        var headers: [String: Any] = [:]
        for (key, value) in object {
            headers[key] = value
        }
        return headers
    }

    private func topViewController(from base: UIViewController?) -> UIViewController? {
        if let navigation = base as? UINavigationController {
            return topViewController(from: navigation.visibleViewController) ?? navigation
        }
        if let tab = base as? UITabBarController {
            return topViewController(from: tab.selectedViewController) ?? tab
        }
        if let presented = base?.presentedViewController {
            return topViewController(from: presented)
        }
        return base
    }
}
