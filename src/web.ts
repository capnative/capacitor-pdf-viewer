import { WebPlugin } from '@capacitor/core';

import type {
  OpenBlobOptions,
  OpenCapacitorFileOptions,
  OpenContentOptions,
  OpenDataOptions,
  OpenFileOptions,
  OpenPathOptions,
  OpenUrlOptions,
  OpenWebPathOptions,
  PdfViewerPlugin,
} from './definitions';
import { createPdfViewerError, isPdfViewerError } from './errors';
import {
  MAX_PDF_BYTES,
  parseOpenBlob,
  parseOpenCapacitorFile,
  parseOpenContent,
  parseOpenData,
  parseOpenFile,
  parseOpenPath,
  parseOpenUrl,
  parseOpenWebPath,
  viewerTitle,
  withPageFragment,
  type ParsedOpenRequest,
} from './options';

const OVERLAY_ID = 'capnative-pdf-viewer';

interface LoadedPdf {
  displayUrl: string;
  revoke: (() => void) | null;
  openInTabUrl: string | null;
}

export class PdfViewerWeb extends WebPlugin implements PdfViewerPlugin {
  private overlay: HTMLElement | null = null;
  private revokeUrl: (() => void) | null = null;
  private abortController: AbortController | null = null;
  private opening = false;
  private requestId = 0;
  private currentPage = 1;
  private previousOverflow = '';

  async openUrl(options: OpenUrlOptions): Promise<void> {
    await this.present(parseOpenUrl(options));
  }

  async openFile(options: OpenFileOptions): Promise<void> {
    parseOpenFile(options);
    throw createPdfViewerError('UNAVAILABLE', 'file:// URIs are only available on iOS and Android.');
  }

  async openCapacitorFile(options: OpenCapacitorFileOptions): Promise<void> {
    parseOpenCapacitorFile(options);
    throw createPdfViewerError('UNAVAILABLE', 'Capacitor file URLs are only available on iOS and Android.');
  }

  async openPath(options: OpenPathOptions): Promise<void> {
    parseOpenPath(options);
    throw createPdfViewerError('UNAVAILABLE', 'Filesystem paths are only available on iOS and Android.');
  }

  async openContent(options: OpenContentOptions): Promise<void> {
    parseOpenContent(options);
    throw createPdfViewerError('UNAVAILABLE', 'content:// URIs are only supported on Android.');
  }

  async openWebPath(options: OpenWebPathOptions): Promise<void> {
    await this.present(parseOpenWebPath(options));
  }

  async openBlob(options: OpenBlobOptions): Promise<void> {
    await this.present(parseOpenBlob(options));
  }

  async openData(options: OpenDataOptions): Promise<void> {
    await this.present(parseOpenData(options));
  }

  private async present(request: ParsedOpenRequest): Promise<void> {
    if (typeof document === 'undefined') {
      throw createPdfViewerError('UNAVAILABLE', 'The PDF viewer requires a browser document.');
    }
    if (this.overlay || this.opening) {
      throw createPdfViewerError('ALREADY_OPEN', 'A PDF viewer is already open. Call close() first.');
    }

    const requestId = ++this.requestId;
    const abort = new AbortController();
    this.abortController = abort;
    this.opening = true;
    this.currentPage = request.page;

    try {
      const loaded = await this.load(request, abort.signal);
      if (requestId !== this.requestId || abort.signal.aborted) {
        loaded.revoke?.();
        throw createPdfViewerError('CANCELLED', 'The viewer was closed before the PDF finished loading.');
      }
      this.revokeUrl = loaded.revoke;
      this.overlay = this.mount(request, loaded);
    } catch (error) {
      if (requestId !== this.requestId || abort.signal.aborted) {
        throw createPdfViewerError('CANCELLED', 'The viewer was closed before the PDF finished loading.');
      }
      throw error;
    } finally {
      if (requestId === this.requestId) {
        this.opening = false;
      }
    }
  }

  async close(): Promise<void> {
    const hadOverlay = this.overlay !== null;
    const page = this.currentPage;
    this.requestId += 1;
    this.opening = false;
    this.abortController?.abort();
    this.abortController = null;
    this.destroyOverlay();
    if (hadOverlay) {
      this.notifyListeners('closed', { page });
    }
  }

  private async load(request: ParsedOpenRequest, signal: AbortSignal): Promise<LoadedPdf> {
    switch (request.source.kind) {
      case 'local-file':
      case 'content':
        throw createPdfViewerError(
          'UNAVAILABLE',
          'Local PDF paths are only available on iOS and Android. On the web, pass an http(s) or relative URL.',
        );
      case 'direct':
      case 'web-path':
        return {
          displayUrl: withPageFragment(request.source.url, request.page),
          revoke: null,
          openInTabUrl: request.source.url,
        };
      case 'remote':
        return this.loadRemote(request.source.url, request.headers, request.page, signal);
    }
  }

  private async loadRemote(
    url: string,
    headers: Record<string, string>,
    page: number,
    signal: AbortSignal,
  ): Promise<LoadedPdf> {
    try {
      const response = await fetch(url, {
        method: 'GET',
        headers,
        credentials: 'omit',
        referrerPolicy: 'no-referrer',
        signal,
      });
      if (!response.ok) {
        throw createPdfViewerError('LOAD_ERROR', `The PDF could not be downloaded (HTTP ${response.status}).`);
      }

      const advertisedLength = Number(response.headers.get('content-length'));
      if (Number.isFinite(advertisedLength) && advertisedLength > MAX_PDF_BYTES) {
        throw createPdfViewerError('LOAD_ERROR', 'The PDF is larger than 200 MB.');
      }

      const blob = await response.blob();
      if (blob.size === 0) {
        throw createPdfViewerError('LOAD_ERROR', 'The PDF download was empty.');
      }
      if (blob.size > MAX_PDF_BYTES) {
        throw createPdfViewerError('LOAD_ERROR', 'The PDF is larger than 200 MB.');
      }
      if (typeof URL.createObjectURL !== 'function') {
        if (Object.keys(headers).length > 0) {
          throw createPdfViewerError(
            'UNAVAILABLE',
            'This browser cannot apply request headers while displaying a PDF.',
          );
        }
        return { displayUrl: withPageFragment(url, page), revoke: null, openInTabUrl: url };
      }

      const objectUrl = URL.createObjectURL(blob);
      return {
        displayUrl: withPageFragment(objectUrl, page),
        revoke: () => URL.revokeObjectURL(objectUrl),
        openInTabUrl: url,
      };
    } catch (error) {
      if (isPdfViewerError(error) || isAbortError(error)) {
        throw error;
      }
      if (Object.keys(headers).length > 0) {
        throw createPdfViewerError(
          'LOAD_ERROR',
          'The PDF could not be downloaded. The server may be blocking cross-origin requests, so the supplied headers were not sent.',
        );
      }
      return {
        displayUrl: withPageFragment(url, page),
        revoke: null,
        openInTabUrl: url,
      };
    }
  }

  private mount(request: ParsedOpenRequest, loaded: LoadedPdf): HTMLElement {
    const title = viewerTitle(request);
    const overlay = document.createElement('div');
    overlay.id = OVERLAY_ID;
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-label', title);
    overlay.style.position = 'fixed';
    overlay.style.inset = '0';
    overlay.style.zIndex = '2147483646';
    overlay.style.background = '#1c1c1e';
    overlay.style.display = 'flex';
    overlay.style.flexDirection = 'column';

    const bar = document.createElement('div');
    bar.style.display = 'flex';
    bar.style.alignItems = 'center';
    bar.style.gap = '8px';
    bar.style.padding = '8px 12px';
    bar.style.color = '#ffffff';

    const titleNode = document.createElement('div');
    titleNode.textContent = title;
    titleNode.style.flex = '1';
    titleNode.style.textAlign = 'center';
    titleNode.style.font = '600 16px system-ui, sans-serif';
    titleNode.style.overflow = 'hidden';
    titleNode.style.whiteSpace = 'nowrap';
    titleNode.style.textOverflow = 'ellipsis';

    const frame = document.createElement('iframe');
    frame.title = title;
    frame.src = loaded.displayUrl;
    frame.setAttribute('referrerpolicy', 'no-referrer');
    frame.style.flex = '1';
    frame.style.width = '100%';
    frame.style.border = '0';
    frame.style.background = '#ffffff';

    const closeButton = createButton('Done', () => {
      void this.close();
    });
    const openButton = createButton('Open', () => {
      if (typeof window === 'undefined' || loaded.openInTabUrl == null) {
        return;
      }
      window.open(loaded.openInTabUrl, '_blank', 'noopener,noreferrer');
    });

    bar.appendChild(closeButton);
    bar.appendChild(titleNode);
    bar.appendChild(openButton);
    overlay.appendChild(bar);
    overlay.appendChild(frame);

    this.previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.body.appendChild(overlay);
    return overlay;
  }

  private destroyOverlay(): void {
    const overlay = this.overlay;
    if (overlay?.parentNode) {
      overlay.parentNode.removeChild(overlay);
    }
    this.overlay = null;
    if (typeof document !== 'undefined') {
      document.body.style.overflow = this.previousOverflow;
    }
    this.revokeUrl?.();
    this.revokeUrl = null;
  }
}

function createButton(label: string, onClick: () => void): HTMLButtonElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = label;
  button.style.background = 'transparent';
  button.style.color = '#ffffff';
  button.style.border = '0';
  button.style.font = '600 16px system-ui, sans-serif';
  button.style.padding = '8px 12px';
  button.style.cursor = 'pointer';
  button.addEventListener('click', onClick);
  return button;
}

function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: string }).name === 'AbortError';
}
