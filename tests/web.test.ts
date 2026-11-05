import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ViewerClosedEvent } from '../src/definitions';
import { isPdfViewerError } from '../src/errors';
import { PdfViewerWeb } from '../src/web';

class FakeNode {
  id = '';
  style: Record<string, string> = {};
  attributes: Record<string, string> = {};
  textContent = '';
  children: FakeNode[] = [];
  parentNode: FakeNode | null = null;
  src = '';
  title = '';
  type = '';
  private listeners = new Map<string, (() => void)[]>();

  constructor(readonly tag: string) {}

  setAttribute(name: string, value: string): void {
    this.attributes[name] = value;
  }

  appendChild(child: FakeNode): FakeNode {
    child.parentNode = this;
    this.children.push(child);
    return child;
  }

  removeChild(child: FakeNode): FakeNode {
    this.children = this.children.filter((entry) => entry !== child);
    child.parentNode = null;
    return child;
  }

  addEventListener(type: string, listener: () => void): void {
    const current = this.listeners.get(type) ?? [];
    current.push(listener);
    this.listeners.set(type, current);
  }

  click(): void {
    for (const listener of this.listeners.get('click') ?? []) {
      listener();
    }
  }
}

const body = new FakeNode('body');

function installDocument(): void {
  body.children = [];
  body.style = { overflow: '' };
  const documentFake = {
    body,
    createElement: (tag: string) => new FakeNode(tag),
  };
  Object.assign(globalThis, { document: documentFake });
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  body.children = [];
  body.style = {};
  Reflect.deleteProperty(globalThis, 'document');
});

describe('PdfViewerWeb', () => {
  it('shows a fetched PDF in an overlay and emits closed', async () => {
    installDocument();
    const created: string[] = [];
    const revoked: string[] = [];
    URL.createObjectURL = (blob: Blob) => {
      const url = `blob:pdf-${blob.size}`;
      created.push(url);
      return url;
    };
    URL.revokeObjectURL = (url: string) => {
      revoked.push(url);
    };
    const fetchMock = vi.fn(
      async () =>
        new Response(new Uint8Array([37, 80, 68, 70]), { status: 200, headers: { 'content-type': 'application/pdf' } }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const viewer = new PdfViewerWeb();
    const closed: ViewerClosedEvent[] = [];
    await viewer.addListener('closed', (event) => {
      closed.push(event);
    });

    await viewer.open({
      url: 'https://example.com/manual.pdf',
      title: 'Manual',
      page: 2,
      headers: { Authorization: 'Bearer token' },
    });

    expect(fetchMock).toHaveBeenCalledOnce();
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.headers).toEqual({ Authorization: 'Bearer token' });
    expect(body.children).toHaveLength(1);
    const overlay = body.children[0];
    expect(overlay?.attributes['aria-label']).toBe('Manual');
    const frame = overlay?.children.find((child) => child.tag === 'iframe');
    expect(frame?.src).toBe('blob:pdf-4#page=2');
    expect(body.style.overflow).toBe('hidden');

    await viewer.close();
    expect(body.children).toHaveLength(0);
    expect(revoked).toEqual(created);
    expect(closed).toEqual([{ page: 2 }]);
    expect(body.style.overflow).toBe('');
  });

  it('embeds a same-origin path without fetching', async () => {
    installDocument();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const viewer = new PdfViewerWeb();

    await viewer.open({ url: './docs/guide.pdf', page: 4 });

    expect(fetchMock).not.toHaveBeenCalled();
    const frame = body.children[0]?.children.find((child) => child.tag === 'iframe');
    expect(frame?.src).toBe('./docs/guide.pdf#page=4');
    await viewer.close();
  });

  it('falls back to the original URL when a headerless fetch fails', async () => {
    installDocument();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      }),
    );
    const viewer = new PdfViewerWeb();

    await viewer.open({ url: 'https://example.com/manual.pdf', page: 1 });

    const frame = body.children[0]?.children.find((child) => child.tag === 'iframe');
    expect(frame?.src).toBe('https://example.com/manual.pdf#page=1');
    await viewer.close();
  });

  it('rejects a failed fetch when custom headers were requested', async () => {
    installDocument();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      }),
    );
    const viewer = new PdfViewerWeb();

    await expect(
      viewer.open({ url: 'https://example.com/manual.pdf', headers: { Authorization: 'Bearer token' } }),
    ).rejects.toMatchObject({
      code: 'LOAD_ERROR',
    });
    expect(body.children).toHaveLength(0);
  });

  it('rejects local paths, a second open, and HTTP errors', async () => {
    installDocument();
    const viewer = new PdfViewerWeb();

    await expect(viewer.open({ url: 'file:///tmp/a.pdf' })).rejects.toMatchObject({ code: 'UNAVAILABLE' });

    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('missing', { status: 404 })),
    );
    await expect(viewer.open({ url: 'https://example.com/missing.pdf' })).rejects.toMatchObject({ code: 'LOAD_ERROR' });

    URL.createObjectURL = () => 'blob:ok';
    URL.revokeObjectURL = () => undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(new Uint8Array([1]), { status: 200 })),
    );
    await viewer.open({ url: 'https://example.com/a.pdf' });
    await expect(viewer.open({ url: 'https://example.com/b.pdf' })).rejects.toMatchObject({ code: 'ALREADY_OPEN' });
    await viewer.close();
    await expect(viewer.close()).resolves.toBeUndefined();
  });

  it('cancels an in-flight open without emitting closed', async () => {
    installDocument();
    let rejectFetch: (error: Error) => void = () => undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn(
        () =>
          new Promise((_resolve, reject) => {
            rejectFetch = reject;
          }),
      ),
    );
    const viewer = new PdfViewerWeb();
    const closed: ViewerClosedEvent[] = [];
    await viewer.addListener('closed', (event) => {
      closed.push(event);
    });

    const opening = viewer.open({ url: 'https://example.com/slow.pdf' });
    const closedCall = viewer.close();
    rejectFetch(Object.assign(new Error('aborted'), { name: 'AbortError' }));

    await expect(opening).rejects.toSatisfy((error: unknown) => isPdfViewerError(error) && error.code === 'CANCELLED');
    await closedCall;
    expect(closed).toEqual([]);
    expect(body.children).toHaveLength(0);
  });
});
