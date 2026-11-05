import PDFKit
import UIKit

final class PdfViewerViewController: UIViewController, UISearchResultsUpdating, UISearchBarDelegate {
    var onOpened: (() -> Void)?
    var onFailed: ((PdfViewerError) -> Void)?
    var onPageChanged: ((Int, Int) -> Void)?
    var onClosed: ((Int, Int) -> Void)?

    private let request: PdfOpenRequest
    private let pdfView = PDFView()
    private let toolbar = UIToolbar()
    private let pageLabel = UILabel()
    private let activityIndicator = UIActivityIndicatorView(style: .large)
    private lazy var searchController = UISearchController(searchResultsController: nil)
    private lazy var previousItem = UIBarButtonItem(
        image: UIImage(systemName: "chevron.left"),
        style: .plain,
        target: self,
        action: #selector(didTapPrevious)
    )
    private lazy var nextItem = UIBarButtonItem(
        image: UIImage(systemName: "chevron.right"),
        style: .plain,
        target: self,
        action: #selector(didTapNext)
    )
    private lazy var shareItem = UIBarButtonItem(
        barButtonSystemItem: .action,
        target: self,
        action: #selector(didTapShare)
    )

    private var loadTask: Task<Void, Never>?
    private var searchTimer: Timer?
    private var loadedFileURL: URL?
    private var ownsFile = false
    private var selections: [PDFSelection] = []
    private var selectionIndex = 0
    private var lastPublishedPage = 0
    private var didOpen = false
    private var didFinish = false

    init(request: PdfOpenRequest) {
        self.request = request
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    deinit {
        loadTask?.cancel()
        searchTimer?.invalidate()
        NotificationCenter.default.removeObserver(self)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        title = request.displayTitle
        navigationItem.largeTitleDisplayMode = .never
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(didTapClose)
        )
        shareItem.isEnabled = false
        navigationItem.rightBarButtonItem = shareItem
        configureSearch()
        configurePdfView()
        configureToolbar()
        configureIndicator()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handlePageChanged),
            name: .PDFViewPageChanged,
            object: pdfView
        )
        updateChrome()
        startLoad()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        pdfView.autoScales = true
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || navigationController?.isBeingDismissed == true {
            finishClose()
        }
    }

    func updateSearchResults(for searchController: UISearchController) {
        searchTimer?.invalidate()
        let query = searchController.searchBar.text ?? ""
        searchTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: false) { [weak self] _ in
            self?.applySearch(query: query)
        }
    }

    func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
        guard isSearching, !selections.isEmpty else {
            return
        }
        focusSelection(at: (selectionIndex + 1) % selections.count)
    }

    private func configureSearch() {
        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Search"
        searchController.searchBar.autocapitalizationType = .none
        searchController.searchBar.returnKeyType = .search
        searchController.searchBar.delegate = self
        navigationItem.searchController = searchController
        navigationItem.hidesSearchBarWhenScrolling = false
        definesPresentationContext = true
    }

    private func configurePdfView() {
        pdfView.translatesAutoresizingMaskIntoConstraints = false
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        pdfView.displaysPageBreaks = true
        pdfView.interpolationQuality = .high
        pdfView.backgroundColor = .secondarySystemBackground
        pdfView.pageBreakMargins = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        view.addSubview(pdfView)
    }

    private func configureToolbar() {
        toolbar.translatesAutoresizingMaskIntoConstraints = false
        pageLabel.font = .preferredFont(forTextStyle: .footnote)
        pageLabel.textAlignment = .center
        pageLabel.adjustsFontForContentSizeCategory = true
        previousItem.accessibilityLabel = "Previous page"
        nextItem.accessibilityLabel = "Next page"
        view.addSubview(toolbar)
        NSLayoutConstraint.activate([
            pdfView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            pdfView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            pdfView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            pdfView.bottomAnchor.constraint(equalTo: toolbar.topAnchor),
            toolbar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            toolbar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            toolbar.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
        ])
    }

    private func configureIndicator() {
        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        activityIndicator.hidesWhenStopped = true
        view.addSubview(activityIndicator)
        NSLayoutConstraint.activate([
            activityIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            activityIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
        activityIndicator.startAnimating()
    }

    private func startLoad() {
        loadTask = Task { [weak self] in
            guard let self else {
                return
            }
            do {
                let loaded = try await PdfLoader.load(self.request)
                if Task.isCancelled {
                    self.deleteOwnedFile(loaded)
                    return
                }
                self.deliver(loaded)
            } catch is CancellationError {
                return
            } catch let error as PdfViewerError {
                self.deliverFailure(error)
            } catch {
                self.deliverFailure(PdfViewerError.loadFailed("The PDF could not be opened."))
            }
        }
    }

    private func deliver(_ loaded: LoadedPdf) {
        if Thread.isMainThread {
            show(loaded)
        } else {
            DispatchQueue.main.async { [weak self] in
                self?.show(loaded)
            }
        }
    }

    private func deliverFailure(_ error: PdfViewerError) {
        if Thread.isMainThread {
            fail(error)
        } else {
            DispatchQueue.main.async { [weak self] in
                self?.fail(error)
            }
        }
    }

    private func show(_ loaded: LoadedPdf) {
        guard !didFinish else {
            deleteOwnedFile(loaded)
            return
        }
        guard let document = PDFDocument(url: loaded.fileURL) else {
            deleteOwnedFile(loaded)
            fail(PdfViewerError.loadFailed("The PDF could not be opened. It may be corrupt or not a PDF."))
            return
        }
        if document.isLocked {
            deleteOwnedFile(loaded)
            fail(PdfViewerError(code: "PASSWORD_PROTECTED", message: "This PDF is password protected."))
            return
        }
        if document.pageCount == 0 {
            deleteOwnedFile(loaded)
            fail(PdfViewerError.loadFailed("The PDF has no pages."))
            return
        }
        if request.page > document.pageCount {
            deleteOwnedFile(loaded)
            fail(PdfViewerError.invalidPage("page is greater than the number of pages in the PDF."))
            return
        }

        loadedFileURL = loaded.fileURL
        ownsFile = loaded.ownsFile
        pdfView.document = document
        if let page = document.page(at: request.page - 1) {
            pdfView.go(to: page)
        }
        activityIndicator.stopAnimating()
        shareItem.isEnabled = true
        didOpen = true
        updateChrome()
        publishPageIfNeeded()
        onOpened?()
        onOpened = nil
    }

    private func fail(_ error: PdfViewerError) {
        guard !didFinish else {
            return
        }
        didFinish = true
        loadTask?.cancel()
        activityIndicator.stopAnimating()
        onFailed?(error)
        onFailed = nil
        dismiss(animated: true)
    }

    private func finishClose() {
        guard !didFinish else {
            return
        }
        didFinish = true
        loadTask?.cancel()
        searchTimer?.invalidate()
        let page = currentPageNumber()
        let count = pdfView.document?.pageCount ?? 0
        deleteOwnedFile()
        if didOpen {
            onClosed?(page, count)
        } else {
            onFailed?(PdfViewerError(code: "CANCELLED", message: "The viewer was closed before the PDF finished loading."))
        }
        onClosed = nil
        onFailed = nil
        onOpened = nil
    }
}

extension PdfViewerViewController {
    @objc private func didTapClose() {
        dismiss(animated: true)
    }

    @objc private func didTapShare() {
        guard let loadedFileURL else {
            return
        }
        let controller = UIActivityViewController(activityItems: [loadedFileURL], applicationActivities: nil)
        if let popover = controller.popoverPresentationController {
            popover.barButtonItem = shareItem
        }
        present(controller, animated: true)
    }

    @objc private func didTapPrevious() {
        if isSearching {
            guard !selections.isEmpty else {
                return
            }
            let next = (selectionIndex - 1 + selections.count) % selections.count
            focusSelection(at: next)
            return
        }
        pdfView.goToPreviousPage(nil)
    }

    @objc private func didTapNext() {
        if isSearching {
            guard !selections.isEmpty else {
                return
            }
            focusSelection(at: (selectionIndex + 1) % selections.count)
            return
        }
        pdfView.goToNextPage(nil)
    }

    @objc private func handlePageChanged() {
        publishPageIfNeeded()
    }

    private var isSearching: Bool {
        let query = searchController.searchBar.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return !query.isEmpty
    }

    private func applySearch(query: String) {
        guard let document = pdfView.document else {
            return
        }
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            selections = []
            selectionIndex = 0
            pdfView.highlightedSelections = nil
            updateChrome()
            return
        }
        selections = document.findString(trimmed, withOptions: .caseInsensitive)
        selectionIndex = 0
        pdfView.highlightedSelections = selections.isEmpty ? nil : selections
        if !selections.isEmpty {
            focusSelection(at: 0)
        } else {
            updateChrome()
        }
    }

    private func focusSelection(at index: Int) {
        guard selections.indices.contains(index) else {
            return
        }
        selectionIndex = index
        pdfView.highlightedSelections = selections
        pdfView.go(to: selections[index])
        updateChrome()
    }

    private func publishPageIfNeeded() {
        guard didOpen, let document = pdfView.document else {
            return
        }
        let page = currentPageNumber()
        guard page != lastPublishedPage else {
            updateChrome()
            return
        }
        lastPublishedPage = page
        updateChrome()
        onPageChanged?(page, document.pageCount)
    }

    private func currentPageNumber() -> Int {
        guard let document = pdfView.document, let page = pdfView.currentPage else {
            return request.page
        }
        let index = document.index(for: page)
        return index >= 0 ? index + 1 : request.page
    }

    private func updateChrome() {
        let count = pdfView.document?.pageCount ?? 0
        let page = count > 0 ? currentPageNumber() : request.page
        let pageText = count > 0 ? "Page \(page) of \(count)" : "Loading"
        if isSearching {
            if selections.isEmpty {
                pageLabel.text = "No matches · \(pageText)"
            } else {
                pageLabel.text = "Match \(selectionIndex + 1) of \(selections.count) · \(pageText)"
            }
            previousItem.accessibilityLabel = "Previous match"
            nextItem.accessibilityLabel = "Next match"
            previousItem.isEnabled = !selections.isEmpty
            nextItem.isEnabled = !selections.isEmpty
        } else {
            pageLabel.text = pageText
            previousItem.accessibilityLabel = "Previous page"
            nextItem.accessibilityLabel = "Next page"
            previousItem.isEnabled = count > 0 && page > 1
            nextItem.isEnabled = count > 0 && page < count
        }
        let labelItem = UIBarButtonItem(customView: pageLabel)
        let leadingSpace = UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil)
        let trailingSpace = UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil)
        toolbar.items = [previousItem, leadingSpace, labelItem, trailingSpace, nextItem]
        pageLabel.sizeToFit()
    }

    private func deleteOwnedFile(_ loaded: LoadedPdf? = nil) {
        let url = loaded?.fileURL ?? loadedFileURL
        let shouldDelete = loaded?.ownsFile ?? ownsFile
        guard shouldDelete, let url else {
            return
        }
        try? FileManager.default.removeItem(at: url)
        if loaded == nil {
            ownsFile = false
        }
    }
}
