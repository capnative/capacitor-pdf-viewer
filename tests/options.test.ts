import { describe, expect, it } from 'vitest';

import { isPdfViewerError } from '../src/errors';
import {
  parseOpenCapacitorFile,
  parseOpenContent,
  parseOpenFile,
  parseOpenPath,
  parseOpenUrl,
} from '../src/options';

describe('open parsers', () => {
  it('accepts a remote URL and applies defaults', () => {
    const request = parseOpenUrl({
      url: '  https://example.com/files/manual.pdf  ',
      title: '  Guide  ',
      headers: { Authorization: 'Bearer token' },
    });

    expect(request).toEqual({
      source: { kind: 'remote', url: 'https://example.com/files/manual.pdf' },
      title: 'Guide',
      page: 1,
      headers: { Authorization: 'Bearer token' },
    });
  });

  it('drops a blank title', () => {
    const request = parseOpenUrl({
      url: 'https://cdn.example.com/docs/Spec%20v1.pdf?download=1',
      title: '   ',
    });

    expect(request.title).toBeUndefined();
  });

  it('accepts each local source only on its own parser', () => {
    expect(parseOpenFile({ url: 'file:///tmp/my%20file.pdf' }).source).toEqual({
      kind: 'local-file',
      path: '/tmp/my file.pdf',
    });
    expect(parseOpenContent({ uri: 'content://com.example/docs/1' }).source).toEqual({
      kind: 'content',
      url: 'content://com.example/docs/1',
    });
    expect(parseOpenCapacitorFile({ url: 'capacitor://localhost/_capacitor_file_/var/mobile/a.pdf' }).source).toEqual({
      kind: 'local-file',
      path: '/var/mobile/a.pdf',
    });
    expect(parseOpenPath({ path: '/var/mobile/Documents/report.pdf' }).source).toEqual({
      kind: 'local-file',
      path: '/var/mobile/Documents/report.pdf',
    });
  });

  it('rejects a source passed to the wrong parser', () => {
    expect(codeOf(() => parseOpenUrl({ url: 'file:///tmp/a.pdf' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenUrl({ url: 'https://localhost/_capacitor_file_/var/a.pdf' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenFile({ url: 'https://example.com/a.pdf' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenCapacitorFile({ url: 'file:///tmp/a.pdf' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenPath({ path: './manual.pdf' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenPath({ path: 'https://example.com/a.pdf' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenContent({ uri: 'file:///tmp/a.pdf' }))).toBe('INVALID_SOURCE');
  });

  it('rejects empty and unsupported sources', () => {
    expect(codeOf(() => parseOpenUrl({ url: '   ' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenUrl({ url: 'manual.pdf' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenUrl({ url: 'javascript:alert(1)' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenUrl(undefined))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenCapacitorFile({ url: 'capacitor://localhost/_capacitor_file_relative' }))).toBe(
      'INVALID_SOURCE',
    );
  });

  it('rejects pages that are not positive integers', () => {
    expect(codeOf(() => parseOpenUrl({ url: 'https://example.com/a.pdf', page: 0 }))).toBe('INVALID_PAGE');
    expect(codeOf(() => parseOpenUrl({ url: 'https://example.com/a.pdf', page: 1.5 }))).toBe('INVALID_PAGE');
    expect(codeOf(() => parseOpenUrl({ url: 'https://example.com/a.pdf', page: -2 }))).toBe('INVALID_PAGE');
  });

  it('rejects unsafe headers on openUrl', () => {
    expect(
      codeOf(() =>
        parseOpenUrl({
          url: 'https://example.com/a.pdf',
          headers: { Authorization: 'Bearer\r\nX-Injected: yes' },
        }),
      ),
    ).toBe('INVALID_HEADERS');
    expect(
      codeOf(() =>
        parseOpenUrl({
          url: 'https://example.com/a.pdf',
          headers: { 'Not A Header': 'value' },
        }),
      ),
    ).toBe('INVALID_HEADERS');
    expect(
      codeOf(() =>
        parseOpenUrl({
          url: 'https://example.com/a.pdf',
          headers: ['nope'] as unknown as Record<string, string>,
        }),
      ),
    ).toBe('INVALID_HEADERS');
  });
});

function codeOf(run: () => void): string | undefined {
  try {
    run();
  } catch (error) {
    if (isPdfViewerError(error)) {
      return error.code;
    }
    throw error;
  }
  return undefined;
}
