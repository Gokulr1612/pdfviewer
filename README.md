# Doc Viewer

An Android app for reading PDF, Word and Excel documents — including the
`.docx`/`.xlsx` files that Google Docs and Sheets export, which differ from
Word's output in ways that break naive renderers.

Everything runs on-device. Nothing is uploaded.

## Status

Phase 1 of the [build plan](#roadmap). The PDF viewer is wired up and the
project builds; the Word and Excel renderers are still to come.

Nothing here has been run on a device yet. CI proves that the project compiles
against the Android SDK and that the format-detection tests pass — not that a
document renders correctly on screen.

| Format | State |
| --- | --- |
| PDF | Viewer wired to `androidx.pdf` |
| XLSX / XLSM | Read and rendered as a scrollable grid |
| DOCX / DOCM | Detected and identified; renderer not yet built |
| PPTX, legacy `.doc`/`.xls`, OpenDocument | Detected, explicitly unsupported |

## Layout

    core/   Pure Kotlin. No Android dependencies at all, so the format logic
            is unit-testable on a plain JVM without a device or emulator.
    app/    The Android application: file intake, navigation and rendering.

Keeping `core` free of Android types is deliberate — the parsing work is both
the hardest part and the part most worth testing, and it should never need an
emulator to verify.

## Getting the app on a phone

Every merge into `master` publishes a [GitHub Release](../../releases) with an
APK attached. Download the `.apk` onto an Android phone and open it — Android
will ask permission to install from that source the first time. Requires
Android 9 (API 28) or newer.

Changes still on a branch get an APK too: open the pull request's CI run and
download the `apk-pr-<number>` artifact, so a change can be tried before it is
merged rather than only after.

This is an Android-only project, so a release contains one APK. There are no
macOS, Windows or iOS builds to publish — that was the point of choosing a
native Android app rather than a cross-platform framework.

### Signed builds (optional)

With no configuration, releases contain a **debug** APK. It installs and runs,
but it is signed with a throwaway key that changes between CI runs, so
installing a new build over an old one fails with a signature mismatch and the
previous version has to be uninstalled first.

Adding four repository secrets removes that and publishes a properly signed
release build alongside the debug one. Create a keystore once:

    keytool -genkeypair -v -keystore release.jks -alias docviewer \
      -keyalg RSA -keysize 2048 -validity 10000

Then add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `SIGNING_STORE_PASSWORD` | the keystore password |
| `SIGNING_KEY_ALIAS` | `docviewer` |
| `SIGNING_KEY_PASSWORD` | the key password |

Keep `release.jks` somewhere safe and out of the repository. Losing it means
future builds cannot upgrade an existing install.

## Building

Requires JDK 17+ and the Android SDK (API 35).

    ./gradlew :core:test          # format detection tests
    ./gradlew :app:assembleDebug  # build the app

## Reading spreadsheets

XLSX is a ZIP of XML, so the reader needs nothing beyond `java.util.zip` and
`XmlPullParser`, both of which Android already bundles. Apache POI was
deliberately not used: its method count strains the dex limit, and the lighter
alternatives depend on StAX, which Android does not ship.

Reading takes two passes over the archive — one for the small shared parts
(sheet list, string table, styles) and one to stream whichever sheet is being
shown — so two sheet bodies are never in memory at once. Reads are bounded and
report when they stopped early rather than silently returning a short sheet.

Two details that a naive reader gets wrong:

- **A number is only a date if the workbook's styles say so.** `45322` is
  either the number 45322 or 31 January 2024 depending on a style index, and
  nothing in the cell itself distinguishes them.
- **Excel's 1900 epoch contains a deliberate bug.** It treats 1900 as a leap
  year for Lotus 1-2-3 compatibility, so serial 60 is a 29 February that never
  existed. Workbooks from classic Mac Excel count from 1904 instead.

## How a file is identified

The app reads files to work out what they are rather than trusting the type
they were labelled with. Apps that share documents routinely declare
`application/octet-stream`, or the wrong Office type, or hand over a
`content://` URI with no filename — believing those labels is the largest
single source of "it won't open" bugs in a document viewer.

Two cases are worth calling out, because most viewers get them wrong:

- **A password-protected `.docx` is not a ZIP.** Encrypted OOXML is wrapped in
  an OLE container, so a naive detector reports it as an unopenable Word 97
  file and the user is never asked for a password.
- **A `.gdoc` or `.gsheet` contains no document.** Google's native files are a
  few hundred bytes of JSON pointing at Drive. They are detected as shortcuts
  and their URL offered for hand-off, rather than failing as corrupt files.

## Roadmap

1. ~~Project skeleton~~
2. ~~File intake and PDF~~ — intent filters, document picker, recents, detection
3. ~~XLSX~~ — an OOXML reader and a Compose grid
4. **DOCX** — a block model, a Compose renderer, and a WebView fallback
5. Polish — search, text selection, large-file behaviour, accessibility
6. Later — PPTX, legacy formats, Drive and OneDrive pickers

Word and Excel rendering targets *readable* fidelity, not a pixel-perfect match
with Word. Because everything stays on-device there is no conversion service to
fall back on, so the app is explicit in the UI when a document uses features it
cannot draw.
