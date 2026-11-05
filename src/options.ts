import type { OpenOptions } from './definitions';
import { createPdfViewerError } from './errors';

/** Downloads larger than this are rejected on every platform. */
export const MAX_PDF_BYTES = 200 * 1024 * 1024;

const CAPACITOR_FILE_MARKER = '/_capacitor_file_';
const HEADER_NAME = /^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/;

export type PdfSource =
  | { kind: 'remote'; url: string }
  | { kind: 'local-file'; path: string }
  | { kind: 'content'; url: string }
  | { kind: 'web-path'; url: string }
  | { kind: 'direct'; url: string };

export interface ParsedOpenRequest {
  source: PdfSource;
  title?: string;
  page: number;
  headers: Record<string, string>;
}

/**
 * Validate `open()` options for the web viewer.
 *
 * Native implementations validate again. A leading `/` is a same-origin URL
 * on the web and a filesystem path on iOS and Android.
 */
export function parseOpenOptions(options: OpenOptions | null | undefined): ParsedOpenRequest {
  if (options == null || typeof options !== 'object') {
    throw createPdfViewerError('INVALID_SOURCE', 'options are required.');
  }

  const url = readUrl(options.url);
  return {
    source: classifyWebSource(url),
    title: readTitle(options.title),
    page: readPage(options.page),
    headers: readHeaders(options.headers),
  };
}

export function viewerTitle(request: ParsedOpenRequest): string {
  if (request.title) {
    return request.title;
  }

  const raw = request.source.kind === 'local-file' ? request.source.path : request.source.url;
  const withoutFragment = raw.split('#')[0] ?? raw;
  const withoutQuery = withoutFragment.split('?')[0] ?? withoutFragment;
  const segment = withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
  if (!segment) {
    return 'PDF';
  }

  try {
    return decodeURIComponent(segment);
  } catch {
    return segment;
  }
}

export function withPageFragment(url: string, page: number): string {
  const base = url.split('#')[0] ?? url;
  return `${base}#page=${page}`;
}

function readUrl(value: unknown): string {
  if (typeof value !== 'string') {
    throw createPdfViewerError('INVALID_SOURCE', 'url is required and must be a string.');
  }
  const url = value.trim();
  if (url.length === 0) {
    throw createPdfViewerError('INVALID_SOURCE', 'url must not be empty.');
  }
  return url;
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

function classifyWebSource(url: string): PdfSource {
  const markerIndex = url.indexOf(CAPACITOR_FILE_MARKER);
  if (markerIndex >= 0) {
    const rawPath = url.slice(markerIndex + CAPACITOR_FILE_MARKER.length);
    const path = decodePath(rawPath.split('?')[0]?.split('#')[0] ?? rawPath);
    if (!path.startsWith('/')) {
      throw createPdfViewerError('INVALID_SOURCE', 'The Capacitor file URL did not contain an absolute path.');
    }
    return { kind: 'local-file', path };
  }

  const scheme = /^([a-zA-Z][a-zA-Z0-9+.-]*):/.exec(url)?.[1]?.toLowerCase();
  if (scheme === 'http' || scheme === 'https') {
    return { kind: 'remote', url };
  }
  if (scheme === 'file') {
    return { kind: 'local-file', path: fileUrlToPath(url) };
  }
  if (scheme === 'content') {
    return { kind: 'content', url };
  }
  if (scheme === 'blob' || scheme === 'data') {
    return { kind: 'direct', url };
  }
  if (scheme) {
    throw createPdfViewerError('INVALID_SOURCE', `The URL scheme "${scheme}:" is not supported.`);
  }
  if ((url.startsWith('/') && !url.startsWith('//')) || url.startsWith('./') || url.startsWith('../')) {
    return { kind: 'web-path', url };
  }

  throw createPdfViewerError(
    'INVALID_SOURCE',
    'url must be an http(s) URL, a file:// URI, a content:// URI, an absolute path, or a web-relative path.',
  );
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
