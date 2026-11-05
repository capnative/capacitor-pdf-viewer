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
 * Options for `PdfViewer.open()`.
 */
export interface OpenOptions {
  /**
   * PDF to display.
   *
   * Accepts an `http:` or `https:` URL, a `file://` URI, a Capacitor converted
   * file URL containing `_capacitor_file_`, or an absolute filesystem path.
   * Android also accepts `content://` URIs. On the web, a root-relative
   * (`/files/a.pdf`) or dot-relative (`./a.pdf`) URL is embedded directly,
   * and `blob:` / `data:` URLs are supported.
   *
   * A leading `/` is a filesystem path on iOS and Android, and a same-origin
   * URL path on the web.
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
   * Extra HTTP headers sent when downloading a remote PDF.
   *
   * Ignored for local files. Names must be HTTP header tokens and values
   * must be strings without CR or LF. On the web, headers are only applied
   * when the browser can `fetch` the file (CORS).
   */
  headers?: Record<string, string>;
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
   *
   * Omitted on web, where the embedded browser viewer does not report a page count.
   */
  pageCount?: number;
}

/**
 * Payload emitted when the visible page changes, including the page the viewer opened on.
 *
 * iOS and Android only. The web viewer does not emit this event.
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
   * Open a PDF in a full-screen in-app viewer.
   *
   * Resolves once the first page is visible. Rejects if the file cannot be
   * loaded, the requested page does not exist, or a viewer is already open.
   * Dismiss the current viewer with `close()` before calling `open()` again.
   *
   * On iOS the viewer is a PDFKit `PDFView` with pinch-zoom, continuous
   * scrolling, text search, and the system share sheet. On Android it is an
   * in-app `PdfRenderer` activity with pinch-zoom, pan, page controls, and
   * share. On the web it is a full-screen iframe overlay.
   */
  open(options: OpenOptions): Promise<void>;

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
   * called. It does not fire if `open()` rejects before the document is shown.
   */
  addListener(eventName: 'closed', listenerFunc: (event: ViewerClosedEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Listen for page changes inside the native viewer.
   *
   * The first event is the page the document opened on. Not emitted on web.
   */
  addListener(eventName: 'pageChanged', listenerFunc: (event: PageChangedEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners registered by this plugin.
   */
  removeAllListeners(): Promise<void>;
}
