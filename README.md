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
| PDF | Viewer wired to `androidx.pdf`; not yet run on a device |
| DOCX / XLSX | Detected and identified; renderer not yet built |
| PPTX, legacy `.doc`/`.xls`, OpenDocument | Detected, explicitly unsupported |

## Layout

    core/   Pure Kotlin. No Android dependencies at all, so the format logic
            is unit-testable on a plain JVM without a device or emulator.
    app/    The Android application: file intake, navigation and rendering.

Keeping `core` free of Android types is deliberate — the parsing work is both
the hardest part and the part most worth testing, and it should never need an
emulator to verify.

## Building

Requires JDK 17+ and the Android SDK (API 35).

    ./gradlew :core:test          # format detection tests
    ./gradlew :app:assembleDebug  # build the app

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
2. **File intake and PDF** — intent filters, document picker, recents, detection
3. XLSX — an OOXML reader and a Compose grid
4. DOCX — a block model, a Compose renderer, and a WebView fallback
5. Polish — search, text selection, large-file behaviour, accessibility
6. Later — PPTX, legacy formats, Drive and OneDrive pickers

Word and Excel rendering targets *readable* fidelity, not a pixel-perfect match
with Word. Because everything stays on-device there is no conversion service to
fall back on, so the app is explicit in the UI when a document uses features it
cannot draw.
