import { registerPlugin } from '@capacitor/core';

import type { PdfViewerPlugin } from './definitions';

const PdfViewer = registerPlugin<PdfViewerPlugin>('PdfViewer');

export * from './definitions';
export { PdfViewer };
