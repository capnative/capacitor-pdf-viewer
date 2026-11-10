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

await PdfViewer.openUrl({
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

Each source has its own method. Pass a `file://` URI from `@capacitor/filesystem` to `openFile()`:

```typescript
import { Directory, Filesystem } from '@capacitor/filesystem';
import { PdfViewer } from '@capnative/capacitor-pdf-viewer';

const { uri } = await Filesystem.getUri({
  path: 'report.pdf',
  directory: Directory.Documents,
});

await PdfViewer.openFile({ url: uri });
```

| Method | Source | Platforms |
| --- | --- | --- |
| `openUrl` | `http:` and `https:` | iOS, Android, Web |
| `openFile` | `file://` | iOS, Android |
| `openCapacitorFile` | Capacitor URL containing `_capacitor_file_` | iOS, Android |
| `openPath` | Absolute filesystem path | iOS, Android |
| `openContent` | `content://` | Android |
| `openWebPath` | Root-relative (`/files/a.pdf`) or dot-relative (`./a.pdf`) URL | Web |
| `openBlob` | `blob:` | Web |
| `openData` | `data:` | Web |

A method rejects with `INVALID_SOURCE` when the value is the wrong kind. Calling a method on a platform that cannot open that source rejects with `UNAVAILABLE`.

Every open method resolves when the first page is visible. It rejects if the file cannot be loaded or a viewer is already open. Call `close()` and wait for `closed` before opening another document.

## Platform setup

No extra Capacitor config block is required.

### iOS

The viewer is a presented `UINavigationController` hosting a PDFKit `PDFView`. Pinch-zoom, continuous vertical scrolling, and page navigation come from PDFKit. The navigation bar has Done, a search field, and the system share sheet. Search jumps between case-insensitive matches.

The plugin does not add `Info.plist` keys. `openUrl()` with `https` works with the default App Transport Security policy. Plain `http` needs an App Transport Security exception in the host app. `openFile()`, `openCapacitorFile()`, and `openPath()` require a file the app sandbox can already read (app documents, cache, or a file the app has security-scoped access to). `openContent()` is not supported on iOS.

Minimum version is iOS 15.

### Android

The viewer is an in-app `Activity`. It uses the platform `android.graphics.pdf.PdfRenderer` (available since API 21) rather than handing the file to another app.

`androidx.pdf:pdf-viewer-fragment` was considered and not used. As of the 1.0.0-beta01 release it is still beta, and it requires a higher minimum SDK than Capacitor 8 (API 24). `PdfRenderer` keeps the plugin free of extra native libraries and runs on every Android version Capacitor supports. The activity renders the current page to a bitmap (up to 2× the screen width, capped for memory), then supports pinch-zoom, pan, previous/next, horizontal swipes, and share. Text search is not available from `PdfRenderer`; search is iOS-only.

The plugin manifest merges `android.permission.INTERNET` and a `FileProvider` used only for the share sheet (`${applicationId}.pdfviewer.fileprovider`). Capacitor apps already include `INTERNET`. Plain `http` URLs passed to `openUrl()` also need the host app to allow cleartext traffic. `openContent()` copies the `content://` URI into the app cache and requires a URI the app is allowed to read. `openPath()` is limited to files the app can already read under scoped storage.

Minimum version is API 24.

### Web

`openUrl()`, `openWebPath()`, `openBlob()`, and `openData()` install a full-screen overlay with the PDF in an iframe, plus Done and Open. Open leaves the overlay in place and loads the original URL in a new tab, which is the fallback when a browser will not paint a PDF inside a frame.

`openUrl()` downloads the file with `fetch` so `headers` are applied, then displays it from a blob URL. If that fetch fails and no custom headers were set, the original URL is embedded directly. Custom headers cannot be applied when the server does not allow the cross-origin fetch. `openWebPath()`, `openBlob()`, and `openData()` embed the source directly. `pageChanged` is not emitted on the web. A root-relative path belongs to `openWebPath()`; an absolute filesystem path belongs to `openPath()`.

`openFile()`, `openCapacitorFile()`, `openPath()`, and `openContent()` reject with `UNAVAILABLE` on the web.

## API

<docgen-index>

* [`openUrl(...)`](#openurl)
* [`openFile(...)`](#openfile)
* [`openCapacitorFile(...)`](#opencapacitorfile)
* [`openPath(...)`](#openpath)
* [`openContent(...)`](#opencontent)
* [`openWebPath(...)`](#openwebpath)
* [`openBlob(...)`](#openblob)
* [`openData(...)`](#opendata)
* [`close()`](#close)
* [`addListener('closed', ...)`](#addlistenerclosed-)
* [`addListener('pageChanged', ...)`](#addlistenerpagechanged-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### openUrl(...)

```typescript
openUrl(options: OpenUrlOptions) => Promise<void>
```

Open an `http:` or `https:` PDF in a full-screen in-app viewer.

Resolves once the first page is visible. Rejects if the file cannot be
loaded, the requested page does not exist, or a viewer is already open.
Dismiss the current viewer with `close()` before opening another document.

On iOS the viewer is a PDFKit `PDFView` with pinch-zoom, continuous
scrolling, text search, and the system share sheet. On Android it is an
in-app `PdfRenderer` activity with pinch-zoom, pan, page controls, and
share. On the web it is a full-screen iframe overlay. The file is
downloaded with `headers` before it is shown.

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#openurloptions">OpenUrlOptions</a></code> |

--------------------


### openFile(...)

```typescript
openFile(options: OpenFileOptions) => Promise<void>
```

Open a `file://` PDF.

iOS and Android only. Rejects with `UNAVAILABLE` on the web.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#openfileoptions">OpenFileOptions</a></code> |

--------------------


### openCapacitorFile(...)

```typescript
openCapacitorFile(options: OpenCapacitorFileOptions) => Promise<void>
```

Open a Capacitor converted file URL containing `_capacitor_file_`.

iOS and Android only. Rejects with `UNAVAILABLE` on the web.

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code><a href="#opencapacitorfileoptions">OpenCapacitorFileOptions</a></code> |

--------------------


### openPath(...)

```typescript
openPath(options: OpenPathOptions) => Promise<void>
```

Open a PDF from an absolute filesystem path.

iOS and Android only. Rejects with `UNAVAILABLE` on the web.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#openpathoptions">OpenPathOptions</a></code> |

--------------------


### openContent(...)

```typescript
openContent(options: OpenContentOptions) => Promise<void>
```

Open a PDF from an Android `content://` URI.

Android only. Rejects with `UNAVAILABLE` on iOS and web.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#opencontentoptions">OpenContentOptions</a></code> |

--------------------


### openWebPath(...)

```typescript
openWebPath(options: OpenWebPathOptions) => Promise<void>
```

Open a root-relative or dot-relative PDF URL in the web viewer.

Web only. Rejects with `UNAVAILABLE` on iOS and Android.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#openwebpathoptions">OpenWebPathOptions</a></code> |

--------------------


### openBlob(...)

```typescript
openBlob(options: OpenBlobOptions) => Promise<void>
```

Open a `blob:` PDF URL in the web viewer.

Web only. Rejects with `UNAVAILABLE` on iOS and Android.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#openbloboptions">OpenBlobOptions</a></code> |

--------------------


### openData(...)

```typescript
openData(options: OpenDataOptions) => Promise<void>
```

Open a `data:` PDF URL in the web viewer.

Web only. Rejects with `UNAVAILABLE` on iOS and Android.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#opendataoptions">OpenDataOptions</a></code> |

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
called. It does not fire if the open call rejects before the document is shown.

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


#### OpenUrlOptions

Options for `PdfViewer.openUrl()`.

| Prop          | Type                                                            | Description                                                                                                                                                                                                        | Default        |
| ------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- |
| **`url`**     | <code>string</code>                                             | Remote PDF. Must be an `http:` or `https:` URL. Plain `http` needs an App Transport Security exception on iOS and cleartext traffic allowed on Android.                                                            |                |
| **`title`**   | <code>string</code>                                             | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                                                                                                            |                |
| **`page`**    | <code>number</code>                                             | 1-based page to show first.                                                                                                                                                                                        | <code>1</code> |
| **`headers`** | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Extra HTTP headers sent when downloading the PDF. Names must be HTTP header tokens and values must be strings without CR or LF. On the web, headers are only applied when the browser can `fetch` the file (CORS). |                |


#### OpenFileOptions

Options for `PdfViewer.openFile()`.

| Prop        | Type                | Description                                                                                       | Default        |
| ----------- | ------------------- | ------------------------------------------------------------------------------------------------- | -------------- |
| **`url`**   | <code>string</code> | Local PDF as a `file://` URI. iOS and Android only. The file must already be readable by the app. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.           |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                       | <code>1</code> |


#### OpenCapacitorFileOptions

Options for `PdfViewer.openCapacitorFile()`.

| Prop        | Type                | Description                                                                                                                              | Default        |
| ----------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`url`**   | <code>string</code> | Local PDF as a Capacitor converted file URL. The URL must contain `_capacitor_file_` followed by an absolute path. iOS and Android only. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                                  |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                                                              | <code>1</code> |


#### OpenPathOptions

Options for `PdfViewer.openPath()`.

| Prop        | Type                | Description                                                                                                                                                    | Default        |
| ----------- | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`path`**  | <code>string</code> | Absolute filesystem path, such as `/var/mobile/Documents/report.pdf`. iOS and Android only. A leading `/` is not a website path; use `openWebPath()` for that. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                                                        |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                                                                                    | <code>1</code> |


#### OpenContentOptions

Options for `PdfViewer.openContent()`.

| Prop        | Type                | Description                                                                                                                  | Default        |
| ----------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`uri`**   | <code>string</code> | Android `content://` URI the app is allowed to read. Android only. The file is copied into the app cache before it is shown. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                      |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                                                  | <code>1</code> |


#### OpenWebPathOptions

Options for `PdfViewer.openWebPath()`.

| Prop        | Type                | Description                                                                                                          | Default        |
| ----------- | ------------------- | -------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`path`**  | <code>string</code> | Same-origin path on the web. A root-relative (`/files/a.pdf`) or dot-relative (`./a.pdf`, `../a.pdf`) URL. Web only. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                              |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                                          | <code>1</code> |


#### OpenBlobOptions

Options for `PdfViewer.openBlob()`.

| Prop        | Type                | Description                                                                             | Default        |
| ----------- | ------------------- | --------------------------------------------------------------------------------------- | -------------- |
| **`url`**   | <code>string</code> | `blob:` URL. Web only.                                                                  |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name. |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                             | <code>1</code> |


#### OpenDataOptions

Options for `PdfViewer.openData()`.

| Prop        | Type                | Description                                                                             | Default        |
| ----------- | ------------------- | --------------------------------------------------------------------------------------- | -------------- |
| **`url`**   | <code>string</code> | `data:` URL. Web only.                                                                  |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name. |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                             | <code>1</code> |


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
| `INVALID_SOURCE` | iOS, Android, Web | The source is missing, empty, or not the kind that method accepts. `title` must be a string when it is set. |
| `INVALID_PAGE` | iOS, Android, Web | `page` is not an integer greater than or equal to 1, or it is past the last page. |
| `INVALID_HEADERS` | iOS, Android, Web | `headers` is not a string map, or a name or value is not a safe HTTP header. |
| `LOAD_ERROR` | iOS, Android, Web | The file could not be downloaded, read, or parsed, or it is larger than 200 MB. |
| `PASSWORD_PROTECTED` | iOS, Android | The PDF requires a password. Password entry is not supported yet. |
| `ALREADY_OPEN` | iOS, Android, Web | An open method was called while a viewer is already open. |
| `CANCELLED` | iOS, Android, Web | The viewer was dismissed before the document finished loading. |
| `UNAVAILABLE` | iOS, Android, Web | The viewer cannot be presented, or that open method is not supported on that platform. |

## Limitations

- Password-protected PDFs are rejected. There is no password argument yet.
- Android zoom is bitmap-based. It stays sharp up to the rendered resolution (about twice the screen width) and gets softer past that. Android does not search text.
- The web viewer cannot report the current page, so `pageChanged` is native-only and `closed.pageCount` is omitted on web. `closed.page` on web is the requested start page.
- Downloads larger than 200 MB are rejected on every platform.
- iOS cannot be compiled in this repository's Linux CI. Verify the Swift package on a Mac with Xcode 15 or newer (`npm run verify:ios`).
- Publishing to npm (`npm publish`) is a separate step. The package is scoped and `publishConfig.access` is `public`.
