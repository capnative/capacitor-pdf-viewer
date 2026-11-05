# Capacitor PDF Viewer

Open PDF files in a full-screen in-app viewer from a Capacitor app. iOS uses PDFKit, Android renders pages with the platform `PdfRenderer`, and the web implementation shows the file in an overlay. The viewer stays inside the app.

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅         |

## Supported platforms

| Platform | Support | Minimum version            |
| -------- | ------- | -------------------------- |
| iOS      | ✅      | iOS 15                     |
| Android  | ✅      | API level 24 (Android 7.0) |
| Web      | ✅      | A browser that can embed a PDF |

## Install

```bash
npm install @capnative/capacitor-pdf-viewer
npx cap sync
```

Peer dependency: `@capacitor/core >= 8.0.0`.

## Basic usage

```typescript
import { PdfViewer } from '@capnative/capacitor-pdf-viewer';

await PdfViewer.addListener('pageChanged', (event) => {
  console.log(`Page ${event.page} of ${event.pageCount}`);
});

const closed = await PdfViewer.addListener('closed', (event) => {
  console.log(`Closed on page ${event.page}`);
});

await PdfViewer.open({
  url: 'https://example.com/manual.pdf',
  title: 'Manual',
  page: 2,
  headers: {
    Authorization: 'Bearer token',
  },
});

await PdfViewer.close();
await closed.remove();
```

A file from `@capacitor/filesystem` can be opened with the URI that plugin returns:

```typescript
import { Directory, Filesystem } from '@capacitor/filesystem';
import { PdfViewer } from '@capnative/capacitor-pdf-viewer';

const { uri } = await Filesystem.getUri({
  path: 'report.pdf',
  directory: Directory.Documents,
});

await PdfViewer.open({ url: uri });
```

`open()` resolves when the first page is visible. It rejects if the file cannot be loaded or a viewer is already open. Call `close()` and wait for `closed` before opening another document.

## Platform setup

No extra Capacitor config block is required.

### iOS

The viewer is a presented `UINavigationController` hosting a PDFKit `PDFView`. Pinch-zoom, continuous vertical scrolling, and page navigation come from PDFKit. The navigation bar has Done, a search field, and the system share sheet. Search jumps between case-insensitive matches.

The plugin does not add `Info.plist` keys. Remote `https` URLs work with the default App Transport Security policy. Plain `http` URLs need an App Transport Security exception in the host app. Local files must already be readable by the app sandbox (app documents, cache, or a file the app has security-scoped access to). `content://` URIs are not supported on iOS.

Minimum version is iOS 15.

### Android

The viewer is an in-app `Activity`. It uses the platform `android.graphics.pdf.PdfRenderer` (available since API 21) rather than handing the file to another app.

`androidx.pdf:pdf-viewer-fragment` was considered and not used. As of the 1.0.0-beta01 release it is still beta, and it requires a higher minimum SDK than Capacitor 8 (API 24). `PdfRenderer` keeps the plugin free of extra native libraries and runs on every Android version Capacitor supports. The activity renders the current page to a bitmap (up to 2× the screen width, capped for memory), then supports pinch-zoom, pan, previous/next, horizontal swipes, and share. Text search is not available from `PdfRenderer`; search is iOS-only.

The plugin manifest merges `android.permission.INTERNET` and a `FileProvider` used only for the share sheet (`${applicationId}.pdfviewer.fileprovider`). Capacitor apps already include `INTERNET`. Plain `http` URLs also need the host app to allow cleartext traffic. `content://` URIs are copied into the app cache and require a URI the app is allowed to read. Absolute paths are limited to files the app can already read under scoped storage.

Minimum version is API 24.

### Web

`open()` installs a full-screen overlay with the PDF in an iframe, plus Done and Open. Open leaves the overlay in place and loads the original URL in a new tab, which is the fallback when a browser will not paint a PDF inside a frame.

Remote files are downloaded with `fetch` so `headers` are applied, then displayed from a blob URL. If that fetch fails and no custom headers were set, the original URL is embedded directly. Custom headers cannot be applied when the server does not allow the cross-origin fetch. `pageChanged` is not emitted on the web. A leading `/` is a same-origin URL path on the web and a filesystem path on iOS and Android.

`file://`, `content://`, and Capacitor `_capacitor_file_` URLs reject with `UNAVAILABLE` on the web.

## API

<docgen-index>

* [`open(...)`](#open)
* [`close()`](#close)
* [`addListener('closed', ...)`](#addlistenerclosed-)
* [`addListener('pageChanged', ...)`](#addlistenerpagechanged-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### open(...)

```typescript
open(options: OpenOptions) => Promise<void>
```

Open a PDF in a full-screen in-app viewer.

Resolves once the first page is visible. Rejects if the file cannot be
loaded, the requested page does not exist, or a viewer is already open.
Dismiss the current viewer with `close()` before calling `open()` again.

On iOS the viewer is a PDFKit `PDFView` with pinch-zoom, continuous
scrolling, text search, and the system share sheet. On Android it is an
in-app `PdfRenderer` activity with pinch-zoom, pan, page controls, and
share. On the web it is a full-screen iframe overlay.

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#openoptions">OpenOptions</a></code> |

--------------------


### close()

```typescript
close() => Promise<void>
```

Close the viewer if one is open.

Resolves after dismissal has been requested. This is safe to call when
no viewer is open. The `closed` event fires after a viewer that had
successfully opened is dismissed.

--------------------


### addListener('closed', ...)

```typescript
addListener(eventName: 'closed', listenerFunc: (event: ViewerClosedEvent) => void) => Promise<PluginListenerHandle>
```

Listen for the viewer closing.

Fires after a presented viewer is dismissed, including when `close()` is
called. It does not fire if `open()` rejects before the document is shown.

| Param              | Type                                                                                |
| ------------------ | ----------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'closed'</code>                                                               |
| **`listenerFunc`** | <code>(event: <a href="#viewerclosedevent">ViewerClosedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('pageChanged', ...)

```typescript
addListener(eventName: 'pageChanged', listenerFunc: (event: PageChangedEvent) => void) => Promise<PluginListenerHandle>
```

Listen for page changes inside the native viewer.

The first event is the page the document opened on. Not emitted on web.

| Param              | Type                                                                              |
| ------------------ | --------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'pageChanged'</code>                                                        |
| **`listenerFunc`** | <code>(event: <a href="#pagechangedevent">PageChangedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Remove all listeners registered by this plugin.

--------------------


### Interfaces


#### OpenOptions

Options for `PdfViewer.open()`.

| Prop          | Type                                                            | Description                                                                                                                                                                                                                                                                                                                                                                                                                                           | Default        |
| ------------- | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`url`**     | <code>string</code>                                             | PDF to display. Accepts an `http:` or `https:` URL, a `file://` URI, a Capacitor converted file URL containing `_capacitor_file_`, or an absolute filesystem path. Android also accepts `content://` URIs. On the web, a root-relative (`/files/a.pdf`) or dot-relative (`./a.pdf`) URL is embedded directly, and `blob:` / `data:` URLs are supported. A leading `/` is a filesystem path on iOS and Android, and a same-origin URL path on the web. |                |
| **`title`**   | <code>string</code>                                             | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                                                                                                                                                                                                                                                                                                                                               |                |
| **`page`**    | <code>number</code>                                             | 1-based page to show first.                                                                                                                                                                                                                                                                                                                                                                                                                           | <code>1</code> |
| **`headers`** | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Extra HTTP headers sent when downloading a remote PDF. Ignored for local files. Names must be HTTP header tokens and values must be strings without CR or LF. On the web, headers are only applied when the browser can `fetch` the file (CORS).                                                                                                                                                                                                      |                |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### ViewerClosedEvent

Payload emitted when the viewer is dismissed after the document was shown.

| Prop            | Type                | Description                                                                                            |
| --------------- | ------------------- | ------------------------------------------------------------------------------------------------------ |
| **`page`**      | <code>number</code> | 1-based page that was visible when the viewer closed.                                                  |
| **`pageCount`** | <code>number</code> | Total number of pages. Omitted on web, where the embedded browser viewer does not report a page count. |


#### PageChangedEvent

Payload emitted when the visible page changes, including the page the viewer opened on.

iOS and Android only. The web viewer does not emit this event.

| Prop            | Type                | Description                            |
| --------------- | ------------------- | -------------------------------------- |
| **`page`**      | <code>number</code> | 1-based page now visible.              |
| **`pageCount`** | <code>number</code> | Total number of pages in the document. |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>

</docgen-api>

## Errors

Rejected calls include a stable `error.code`. Match on `code`, not on the message text.

| Code | Platforms | Meaning |
| ---- | --------- | ------- |
| `INVALID_SOURCE` | iOS, Android, Web | `url` is missing, empty, or uses an unsupported scheme for that platform. `title` must be a string when it is set. |
| `INVALID_PAGE` | iOS, Android, Web | `page` is not an integer greater than or equal to 1, or it is past the last page. |
| `INVALID_HEADERS` | iOS, Android, Web | `headers` is not a string map, or a name or value is not a safe HTTP header. |
| `LOAD_ERROR` | iOS, Android, Web | The file could not be downloaded, read, or parsed, or it is larger than 200 MB. |
| `PASSWORD_PROTECTED` | iOS, Android | The PDF requires a password. Password entry is not supported yet. |
| `ALREADY_OPEN` | iOS, Android, Web | `open()` was called while a viewer is already open. |
| `CANCELLED` | iOS, Android, Web | The viewer was dismissed before the document finished loading. |
| `UNAVAILABLE` | iOS, Android, Web | The viewer cannot be presented, or the source is not supported on that platform. |

## Limitations

- Password-protected PDFs are rejected. There is no password argument yet.
- Android zoom is bitmap-based. It stays sharp up to the rendered resolution (about twice the screen width) and gets softer past that. Android does not search text.
- The web viewer cannot report the current page, so `pageChanged` is native-only and `closed.pageCount` is omitted on web. `closed.page` on web is the requested start page.
- Downloads larger than 200 MB are rejected on every platform.
- iOS cannot be compiled in this repository's Linux CI. Verify the Swift package on a Mac with Xcode 15 or newer (`npm run verify:ios`).
- Publishing to npm (`npm publish`) is a separate step. The package is scoped and `publishConfig.access` is `public`.
