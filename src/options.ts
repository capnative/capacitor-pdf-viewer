import type {
  OpenCapacitorFileOptions,
  OpenContentOptions,
  OpenFileOptions,
  OpenPathOptions,
  OpenUrlOptions,
} from './definitions';
import { createPdfViewerError } from './errors';

/** Downloads larger than this are rejected on every platform. */
export const MAX_PDF_BYTES = 200 * 1024 * 1024;

const CAPACITOR_FILE_MARKER = '/_capacitor_file_';
const HEADER_NAME = /^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/;
const SCHEME = /^([a-zA-Z][a-zA-Z0-9+.-]*):/;

export type PdfSource =
  | { kind: 'remote'; url: string }
  | { kind: 'local-file'; path: string }
  | { kind: 'content'; url: string };

export interface ParsedOpenRequest {
  source: PdfSource;
  title?: string;
  page: number;
  headers: Record<string, string>;
}

/**
 * Validate `openUrl()` options.
 *
 * Native implementations validate again.
 */
export function parseOpenUrl(options: OpenUrlOptions | null | undefined): ParsedOpenRequest {
  const raw = readOptions(options);
  const url = readString(raw, 'url');
  if (url.includes(CAPACITOR_FILE_MARKER) || !isHttpUrl(url)) {
    throw createPdfViewerError('INVALID_SOURCE', 'url must be an http: or https: URL.');
  }
  return {
    source: { kind: 'remote', url },
    ...readChrome(raw, readHeaders(raw.headers)),
  };
}

/** Validate `openFile()` options. The result is a filesystem path. */
export function parseOpenFile(options: OpenFileOptions | null | undefined): ParsedOpenRequest {
  const raw = readOptions(options);
  const url = readString(raw, 'url');
  if (schemeOf(url) !== 'file') {
    throw createPdfViewerError('INVALID_SOURCE', 'url must be a file:// URI.');
  }
  return {
    source: { kind: 'local-file', path: fileUrlToPath(url) },
    ...readChrome(raw),
  };
}

/** Validate `openCapacitorFile()` options. The result is a filesystem path. */
export function parseOpenCapacitorFile(options: OpenCapacitorFileOptions | null | undefined): ParsedOpenRequest {
  const raw = readOptions(options);
  const url = readString(raw, 'url');
  const markerIndex = url.indexOf(CAPACITOR_FILE_MARKER);
  if (markerIndex < 0) {
    throw createPdfViewerError('INVALID_SOURCE', 'url must be a Capacitor file URL containing _capacitor_file_.');
  }
  const rawPath = url.slice(markerIndex + CAPACITOR_FILE_MARKER.length);
  const path = decodePath(rawPath.split('?')[0]?.split('#')[0] ?? rawPath);
  if (!path.startsWith('/')) {
    throw createPdfViewerError('INVALID_SOURCE', 'The Capacitor file URL did not contain an absolute path.');
  }
  return {
    source: { kind: 'local-file', path },
    ...readChrome(raw),
  };
}

/** Validate `openPath()` options. */
export function parseOpenPath(options: OpenPathOptions | null | undefined): ParsedOpenRequest {
  const raw = readOptions(options);
  const path = readString(raw, 'path');
  if (!path.startsWith('/') || path.startsWith('//')) {
    throw createPdfViewerError('INVALID_SOURCE', 'path must be an absolute filesystem path.');
  }
  return {
    source: { kind: 'local-file', path },
    ...readChrome(raw),
  };
}

/** Validate `openContent()` options. */
export function parseOpenContent(options: OpenContentOptions | null | undefined): ParsedOpenRequest {
  const raw = readOptions(options);
  const uri = readString(raw, 'uri');
  if (schemeOf(uri) !== 'content') {
    throw createPdfViewerError('INVALID_SOURCE', 'uri must be a content:// URI.');
  }
  return {
    source: { kind: 'content', url: uri },
    ...readChrome(raw),
  };
}

function readOptions(options: object | null | undefined): Record<string, unknown> {
  if (options == null || typeof options !== 'object' || Array.isArray(options)) {
    throw createPdfViewerError('INVALID_SOURCE', 'options are required.');
  }
  return options as Record<string, unknown>;
}

function readChrome(
  options: Record<string, unknown>,
  headers: Record<string, string> = {},
): Pick<ParsedOpenRequest, 'title' | 'page' | 'headers'> {
  return {
    title: readTitle(options.title),
    page: readPage(options.page),
    headers,
  };
}

function readString(options: Record<string, unknown>, key: string): string {
  const value = options[key];
  if (typeof value !== 'string') {
    throw createPdfViewerError('INVALID_SOURCE', `${key} is required and must be a string.`);
  }
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    throw createPdfViewerError('INVALID_SOURCE', `${key} must not be empty.`);
  }
  return trimmed;
}

function readTitle(value: unknown): string | undefined {
  if (value == null) {
    return undefined;
  }
  if (typeof value !== 'string') {
    throw createPdfViewerError('INVALID_SOURCE', 'title must be a string.');
  }
  const title = value.trim();
  return title.length > 0 ? title : undefined;
}

function readPage(value: unknown): number {
  if (value == null) {
    return 1;
  }
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1) {
    throw createPdfViewerError('INVALID_PAGE', 'page must be an integer greater than or equal to 1.');
  }
  return value;
}

function readHeaders(value: unknown): Record<string, string> {
  if (value == null) {
    return {};
  }
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw createPdfViewerError('INVALID_HEADERS', 'headers must be an object whose values are strings.');
  }

  const headers: Record<string, string> = {};
  for (const [name, headerValue] of Object.entries(value)) {
    if (typeof headerValue !== 'string') {
      throw createPdfViewerError('INVALID_HEADERS', 'Header values must be strings.');
    }
    if (!HEADER_NAME.test(name) || /[\r\n]/.test(headerValue)) {
      throw createPdfViewerError('INVALID_HEADERS', 'Header names and values must not be empty or contain newlines.');
    }
    headers[name] = headerValue;
  }
  return headers;
}

function schemeOf(url: string): string | undefined {
  return SCHEME.exec(url)?.[1]?.toLowerCase();
}

function isHttpUrl(url: string): boolean {
  const scheme = schemeOf(url);
  return scheme === 'http' || scheme === 'https';
}

function fileUrlToPath(url: string): string {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'file:') {
      throw new Error('not a file URL');
    }
    return decodePath(parsed.pathname);
  } catch (error) {
    if (error instanceof Error && error.name === 'PdfViewerError') {
      throw error;
    }
    throw createPdfViewerError('INVALID_SOURCE', 'The file:// URI could not be parsed.');
  }
}

function decodePath(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}
