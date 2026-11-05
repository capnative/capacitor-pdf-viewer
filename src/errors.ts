import type { PdfViewerErrorCode } from './definitions';

export type PdfViewerError = Error & { code: PdfViewerErrorCode };

export function createPdfViewerError(code: PdfViewerErrorCode, message: string): PdfViewerError {
  const error = new Error(message) as PdfViewerError;
  error.name = 'PdfViewerError';
  error.code = code;
  return error;
}

export function isPdfViewerError(error: unknown): error is PdfViewerError {
  return (
    typeof error === 'object' &&
    error !== null &&
    (error as { name?: string }).name === 'PdfViewerError' &&
    typeof (error as { code?: unknown }).code === 'string'
  );
}
