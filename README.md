# Capacitor PDF Viewer

Open PDF files in a full-screen in-app viewer from a Capacitor app. iOS uses PDFKit, and Android renders pages with the platform `PdfRenderer`. The viewer stays inside the app.

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅         |

## Supported platforms

| Platform | Support | Minimum version            |
| -------- | ------- | -------------------------- |
| iOS      | ✅      | iOS 15                     |
| Android  | ✅      | API level 24 (Android 7.0) |

## Install

```bash
npm install @capnative/capacitor-pdf-viewer
npx cap sync
```

## Basic usage

```typescript
import { PdfViewer } from '@capnative/capacitor-pdf-viewer';

await PdfViewer.openUrl({
  url: 'https://example.com/manual.pdf',
  title: 'Manual',
});
```

## API

<docgen-index>

* [`openUrl(...)`](#openurl)
* [`openUri(...)`](#openuri)
* [`openAsset(...)`](#openasset)
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
share. The file is downloaded with `headers` before it is shown.

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#openurloptions">OpenUrlOptions</a></code> |

--------------------


### openUri(...)

```typescript
openUri(options: OpenUriOptions) => Promise<void>
```

Open a local PDF from a URI or an absolute filesystem path.

Resolves once the first page is visible. Rejects if the file cannot be
read, the requested page does not exist, or a viewer is already open.

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#openurioptions">OpenUriOptions</a></code> |

--------------------


### openAsset(...)

```typescript
openAsset(options: OpenAssetOptions) => Promise<void>
```

Open a PDF shipped with the app.

The path is relative to the web asset directory. On iOS the file is read
from the app bundle. On Android it is copied out of `assets` before it
is shown.

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#openassetoptions">OpenAssetOptions</a></code> |

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

Listen for page changes inside the viewer.

The first event is the page the document opened on.

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

| Prop          | Type                                                            | Description                                                                                                                                             | Default        |
| ------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`url`**     | <code>string</code>                                             | Remote PDF. Must be an `http:` or `https:` URL. Plain `http` needs an App Transport Security exception on iOS and cleartext traffic allowed on Android. |                |
| **`title`**   | <code>string</code>                                             | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                                                 |                |
| **`page`**    | <code>number</code>                                             | 1-based page to show first.                                                                                                                             | <code>1</code> |
| **`headers`** | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Extra HTTP headers sent when downloading the PDF. Names must be HTTP header tokens and values must be strings without CR or LF.                         |                |


#### OpenUriOptions

Options for `PdfViewer.openUri()`.

| Prop        | Type                | Description                                                                                                                                                                                                                                                                                                         | Default        |
| ----------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`uri`**   | <code>string</code> | Local PDF the app is allowed to read. A `file://` URI, an Android `content://` URI, a Capacitor file URL containing `_capacitor_file_`, or an absolute filesystem path such as `/var/mobile/Documents/report.pdf`. `content://` URIs are copied into the app cache on Android and reject with `UNAVAILABLE` on iOS. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                                                                                                                                                                                                             |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                                                                                                                                                                                                                                         | <code>1</code> |


#### OpenAssetOptions

Options for `PdfViewer.openAsset()`.

| Prop        | Type                | Description                                                                                                                                                                                              | Default        |
| ----------- | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`path`**  | <code>string</code> | PDF shipped with the app, relative to the web asset directory. `files/manual.pdf` reads the file at `public/files/manual.pdf` in the app bundle. A leading slash is ignored. `..` segments are rejected. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                                                                                                  |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                                                                                                                              | <code>1</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### ViewerClosedEvent

Payload emitted when the viewer is dismissed after the document was shown.

| Prop            | Type                | Description                                           |
| --------------- | ------------------- | ----------------------------------------------------- |
| **`page`**      | <code>number</code> | 1-based page that was visible when the viewer closed. |
| **`pageCount`** | <code>number</code> | Total number of pages.                                |


#### PageChangedEvent

Payload emitted when the visible page changes, including the page the viewer opened on.

| Prop            | Type                | Description                            |
| --------------- | ------------------- | -------------------------------------- |
| **`page`**      | <code>number</code> | 1-based page now visible.              |
| **`pageCount`** | <code>number</code> | Total number of pages in the document. |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{
 [P in K]: T;
 }</code>

</docgen-api>

## Errors

Rejected calls include a stable `error.code`. Match on `code`, not on the message text.

| Code | Platforms | Meaning |
| ---- | --------- | ------- |
| `INVALID_SOURCE` | iOS, Android | The source is missing, empty, or not the kind that method accepts. `title` must be a string when it is set. |
| `INVALID_PAGE` | iOS, Android | `page` is not an integer greater than or equal to 1, or it is past the last page. |
| `INVALID_HEADERS` | iOS, Android | `headers` is not a string map, or a name or value is not a safe HTTP header. |
| `LOAD_ERROR` | iOS, Android | The file could not be downloaded, read, or parsed, or it is larger than 200 MB. |
| `PASSWORD_PROTECTED` | iOS, Android | The PDF requires a password. Password entry is not supported yet. |
| `ALREADY_OPEN` | iOS, Android | An open method was called while a viewer is already open. |
| `CANCELLED` | iOS, Android | The viewer was dismissed before the document finished loading. |
| `UNAVAILABLE` | iOS, Android | The viewer cannot be presented, or that open method is not supported on that platform. |