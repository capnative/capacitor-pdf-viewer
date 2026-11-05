import { describe, expect, it } from 'vitest';

import { isPdfViewerError } from '../src/errors';
import { parseOpenOptions, viewerTitle, withPageFragment } from '../src/options';

describe('parseOpenOptions', () => {
  it('accepts a remote URL and applies defaults', () => {
    const request = parseOpenOptions({
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
    expect(viewerTitle(request)).toBe('Guide');
  });

  it('uses the file name when the title is blank', () => {
    const request = parseOpenOptions({
      url: 'https://cdn.example.com/docs/Spec%20v1.pdf?download=1',
      title: '   ',
    });

    expect(request.title).toBeUndefined();
    expect(viewerTitle(request)).toBe('Spec v1.pdf');
  });

  it('treats a leading slash as a web path', () => {
    expect(parseOpenOptions({ url: '/assets/manual.pdf', page: 3 }).source).toEqual({
      kind: 'web-path',
      url: '/assets/manual.pdf',
    });
    expect(withPageFragment('/assets/manual.pdf', 3)).toBe('/assets/manual.pdf#page=3');
  });

  it('classifies file, content, blob, and Capacitor file URLs', () => {
    expect(parseOpenOptions({ url: 'file:///tmp/my%20file.pdf' }).source).toEqual({
      kind: 'local-file',
      path: '/tmp/my file.pdf',
    });
    expect(parseOpenOptions({ url: 'content://com.example/docs/1' }).source).toEqual({
      kind: 'content',
      url: 'content://com.example/docs/1',
    });
    expect(parseOpenOptions({ url: 'blob:https://localhost/uuid' }).source.kind).toBe('direct');
    expect(parseOpenOptions({ url: 'capacitor://localhost/_capacitor_file_/var/mobile/a.pdf' }).source).toEqual({
      kind: 'local-file',
      path: '/var/mobile/a.pdf',
    });
  });

  it('rejects empty, relative, and unsupported sources', () => {
    expect(codeOf(() => parseOpenOptions({ url: '   ' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenOptions({ url: 'manual.pdf' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenOptions({ url: 'javascript:alert(1)' }))).toBe('INVALID_SOURCE');
    expect(codeOf(() => parseOpenOptions(undefined))).toBe('INVALID_SOURCE');
  });

  it('rejects pages that are not positive integers', () => {
    expect(codeOf(() => parseOpenOptions({ url: 'https://example.com/a.pdf', page: 0 }))).toBe('INVALID_PAGE');
    expect(codeOf(() => parseOpenOptions({ url: 'https://example.com/a.pdf', page: 1.5 }))).toBe('INVALID_PAGE');
    expect(codeOf(() => parseOpenOptions({ url: 'https://example.com/a.pdf', page: -2 }))).toBe('INVALID_PAGE');
  });

  it('rejects unsafe headers', () => {
    expect(
      codeOf(() =>
        parseOpenOptions({
          url: 'https://example.com/a.pdf',
          headers: { Authorization: 'Bearer\r\nX-Injected: yes' },
        }),
      ),
    ).toBe('INVALID_HEADERS');
    expect(
      codeOf(() =>
        parseOpenOptions({
          url: 'https://example.com/a.pdf',
          headers: { 'Not A Header': 'value' },
        }),
      ),
    ).toBe('INVALID_HEADERS');
    expect(
      codeOf(() =>
        parseOpenOptions({
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
