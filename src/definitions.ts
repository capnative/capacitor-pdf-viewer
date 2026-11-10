import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Stable error codes reported as `error.code` when a call rejects.
 *
 * Handle errors by `code`. Message text can change.
 */
export type PdfViewerErrorCode =
  | 'INVALID_SOURCE'
  | 'INVALID_PAGE'
  | 'INVALID_HEADERS'
  | 'LOAD_ERROR'
  | 'PASSWORD_PROTECTED'
  | 'ALREADY_OPEN'
  | 'CANCELLED'
  | 'UNAVAILABLE';

/**
 * Options for `PdfViewer.openUrl()`.
 */
export interface OpenUrlOptions {
  /**
   * Remote PDF.
   *
   * Must be an `http:` or `https:` URL. Plain `http` needs an App Transport
   * Security exception on iOS and cleartext traffic allowed on Android.
   */
  url: string;

  /**
   * Title shown in the viewer chrome.
   *
   * When omitted or blank, the viewer uses the file name.
   */
  title?: string;

  /**
   * 1-based page to show first.
   *
   * @default 1
   */
  page?: number;

  /**
   * Extra HTTP headers sent when downloading the PDF.
   *
   * Names must be HTTP header tokens and values must be strings without CR
   * or LF.
   */
  headers?: Record<string, string>;
}

/**
 * Options for `PdfViewer.openFile()`.
 */
export interface OpenFileOptions {
  /**
   * Local PDF as a `file://` URI.
   *
   * iOS and Android only. The file must already be readable by the app.
   */
  url: string;

  /**
   * Title shown in the viewer chrome.
   *
   * When omitted or blank, the viewer uses the file name.
   */
  title?: string;

  /**
   * 1-based page to show first.
   *
   * @default 1
   */
  page?: number;
}

/**
 * Options for `PdfViewer.openCapacitorFile()`.
 */
export interface OpenCapacitorFileOptions {
  /**
   * Local PDF as a Capacitor converted file URL.
   *
   * The URL must contain `_capacitor_file_` followed by an absolute path.
   * iOS and Android only.
   */
  url: string;

  /**
   * Title shown in the viewer chrome.
   *
   * When omitted or blank, the viewer uses the file name.
   */
  title?: string;

  /**
   * 1-based page to show first.
   *
   * @default 1
   */
  page?: number;
}

/**
 * Options for `PdfViewer.openPath()`.
 */
export interface OpenPathOptions {
  /**
   * Absolute filesystem path, such as `/var/mobile/Documents/report.pdf`.
   *
   * iOS and Android only.
   */
  path: string;

  /**
   * Title shown in the viewer chrome.
   *
   * When omitted or blank, the viewer uses the file name.
   */
  title?: string;

  /**
   * 1-based page to show first.
   *
   * @default 1
   */
  page?: number;
}

/**
 * Options for `PdfViewer.openContent()`.
 */
export interface OpenContentOptions {
  /**
   * Android `content://` URI the app is allowed to read.
   *
   * Android only. The file is copied into the app cache before it is shown.
   */
  uri: string;

  /**
   * Title shown in the viewer chrome.
   *
   * When omitted or blank, the viewer uses the file name.
   */
  title?: string;

  /**
   * 1-based page to show first.
   *
   * @default 1
   */
  page?: number;
}

/**
 * Payload emitted when the viewer is dismissed after the document was shown.
 */
export interface ViewerClosedEvent {
  /**
   * 1-based page that was visible when the viewer closed.
   */
  page: number;

  /**
   * Total number of pages.
   */
  pageCount: number;
}

/**
 * Payload emitted when the visible page changes, including the page the viewer opened on.
 */
export interface PageChangedEvent {
  /**
   * 1-based page now visible.
   */
  page: number;

  /**
   * Total number of pages in the document.
   */
  pageCount: number;
}

export interface PdfViewerPlugin {
  /**
   * Open an `http:` or `https:` PDF in a full-screen in-app viewer.
   *
   * Resolves once the first page is visible. Rejects if the file cannot be
   * loaded, the requested page does not exist, or a viewer is already open.
   * Dismiss the current viewer with `close()` before opening another document.
   *
   * On iOS the viewer is a PDFKit `PDFView` with pinch-zoom, continuous
   * scrolling, text search, and the system share sheet. On Android it is an
   * in-app `PdfRenderer` activity with pinch-zoom, pan, page controls, and
   * share. The file is downloaded with `headers` before it is shown.
   */
  openUrl(options: OpenUrlOptions): Promise<void>;

  /**
   * Open a `file://` PDF.
   *
   * iOS and Android only.
   */
  openFile(options: OpenFileOptions): Promise<void>;

  /**
   * Open a Capacitor converted file URL containing `_capacitor_file_`.
   *
   * iOS and Android only.
   */
  openCapacitorFile(options: OpenCapacitorFileOptions): Promise<void>;

  /**
   * Open a PDF from an absolute filesystem path.
   *
   * iOS and Android only.
   */
  openPath(options: OpenPathOptions): Promise<void>;

  /**
   * Open a PDF from an Android `content://` URI.
   *
   * Android only. Rejects with `UNAVAILABLE` on iOS.
   */
  openContent(options: OpenContentOptions): Promise<void>;

  /**
   * Close the viewer if one is open.
   *
   * Resolves after dismissal has been requested. This is safe to call when
   * no viewer is open. The `closed` event fires after a viewer that had
   * successfully opened is dismissed.
   */
  close(): Promise<void>;

  /**
   * Listen for the viewer closing.
   *
   * Fires after a presented viewer is dismissed, including when `close()` is
   * called. It does not fire if the open call rejects before the document is shown.
   */
  addListener(eventName: 'closed', listenerFunc: (event: ViewerClosedEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Listen for page changes inside the viewer.
   *
   * The first event is the page the document opened on.
   */
  addListener(eventName: 'pageChanged', listenerFunc: (event: PageChangedEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners registered by this plugin.
   */
  removeAllListeners(): Promise<void>;
}
