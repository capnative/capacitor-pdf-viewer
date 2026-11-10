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
* [`openFile(...)`](#openfile)
* [`openCapacitorFile(...)`](#opencapacitorfile)
* [`openPath(...)`](#openpath)
* [`openContent(...)`](#opencontent)
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


### openFile(...)

```typescript
openFile(options: OpenFileOptions) => Promise<void>
```

Open a `file://` PDF.

iOS and Android only.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#openfileoptions">OpenFileOptions</a></code> |

--------------------


### openCapacitorFile(...)

```typescript
openCapacitorFile(options: OpenCapacitorFileOptions) => Promise<void>
```

Open a Capacitor converted file URL containing `_capacitor_file_`.

iOS and Android only.

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code><a href="#opencapacitorfileoptions">OpenCapacitorFileOptions</a></code> |

--------------------


### openPath(...)

```typescript
openPath(options: OpenPathOptions) => Promise<void>
```

Open a PDF from an absolute filesystem path.

iOS and Android only.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#openpathoptions">OpenPathOptions</a></code> |

--------------------


### openContent(...)

```typescript
openContent(options: OpenContentOptions) => Promise<void>
```

Open a PDF from an Android `content://` URI.

Android only. Rejects with `UNAVAILABLE` on iOS.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#opencontentoptions">OpenContentOptions</a></code> |

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

| Prop        | Type                | Description                                                                                 | Default        |
| ----------- | ------------------- | ------------------------------------------------------------------------------------------- | -------------- |
| **`path`**  | <code>string</code> | Absolute filesystem path, such as `/var/mobile/Documents/report.pdf`. iOS and Android only. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.     |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                 | <code>1</code> |


#### OpenContentOptions

Options for `PdfViewer.openContent()`.

| Prop        | Type                | Description                                                                                                                  | Default        |
| ----------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------- | -------------- |
| **`uri`**   | <code>string</code> | Android `content://` URI the app is allowed to read. Android only. The file is copied into the app cache before it is shown. |                |
| **`title`** | <code>string</code> | Title shown in the viewer chrome. When omitted or blank, the viewer uses the file name.                                      |                |
| **`page`**  | <code>number</code> | 1-based page to show first.                                                                                                  | <code>1</code> |


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