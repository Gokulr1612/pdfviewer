package com.gokul.docviewer.core

import java.io.InputStream
import java.util.zip.ZipInputStream

/** How much the detector trusts its own answer. */
enum class Confidence {
    /** Determined from the bytes of the file. */
    CONTENT,

    /** Determined from the filename because the content was inconclusive. */
    EXTENSION,

    /** Nothing matched. */
    NONE,
}

data class Detection(
    val format: DocumentFormat,
    val confidence: Confidence,
    /** Why the detector decided this. Shown in diagnostics, logged on failure. */
    val reason: String,
) {
    val isSupported: Boolean get() = format.isSupported
}

/**
 * Works out what a file actually is by reading it, not by believing what it
 * was labelled.
 *
 * This matters more than it sounds. Apps that share files routinely declare
 * `application/octet-stream`, or the wrong Office MIME type, or hand over a
 * `content://` URI with no filename at all. Trusting the declared type is the
 * single largest source of "it won't open" bugs in document viewers, so the
 * filename is used only as a tie-breaker when the bytes are genuinely
 * ambiguous.
 */
object FormatDetector {

    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val RTF_MAGIC = "{\\rtf".toByteArray(Charsets.US_ASCII)

    // Local file header, empty archive, and spanned archive respectively.
    private val ZIP_LOCAL = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ZIP_EMPTY = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
    private val ZIP_SPANNED = byteArrayOf(0x50, 0x4B, 0x07, 0x08)

    /** OLE2 / Compound File Binary Format, used by all pre-2007 Office files. */
    private val OLE_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    /**
     * A PDF header should be at offset 0, but files with leading junk are
     * common enough in the wild that every real reader tolerates them.
     */
    private const val PDF_SEARCH_WINDOW = 1024

    /** Enough to reach the CFB directory in practice, bounded so we never read a whole file. */
    private const val OLE_SCAN_BYTES = 64 * 1024

    private const val HEADER_BYTES = 4096

    /** Guards against a hostile or broken archive with a huge entry count. */
    private const val MAX_ZIP_ENTRIES = 512
    private const val MAX_CONTENT_TYPES_BYTES = 1 * 1024 * 1024

    /**
     * @param source the file's bytes.
     * @param fileName optional display name, used only as a fallback.
     */
    fun detect(source: ByteSource, fileName: String? = null): Detection {
        val header = try {
            source.readHeader(HEADER_BYTES)
        } catch (e: Exception) {
            return fallbackToExtension(fileName, "could not read the file: ${e.message}")
        }

        if (header.isEmpty()) {
            return fallbackToExtension(fileName, "the file is empty")
        }

        header.indexOf(PDF_MAGIC, PDF_SEARCH_WINDOW).let { at ->
            if (at == 0) return Detection(DocumentFormat.PDF, Confidence.CONTENT, "%PDF- header")
            if (at > 0) {
                return Detection(
                    DocumentFormat.PDF, Confidence.CONTENT,
                    "%PDF- header at offset $at, after $at bytes of leading data",
                )
            }
        }

        if (header.startsWith(RTF_MAGIC)) {
            return Detection(DocumentFormat.RTF, Confidence.CONTENT, "{\\rtf header")
        }

        if (header.startsWith(OLE_MAGIC)) {
            return detectOle(source, fileName)
        }

        if (header.startsWith(ZIP_LOCAL) ||
            header.startsWith(ZIP_EMPTY) ||
            header.startsWith(ZIP_SPANNED)
        ) {
            return detectZip(source, fileName)
        }

        detectGoogleShortcut(header)?.let { return it }

        return fallbackToExtension(fileName, "no known signature in the first ${header.size} bytes")
    }

    // ---- ZIP-based formats (OOXML, ODF) ------------------------------------

    private fun detectZip(source: ByteSource, fileName: String?): Detection {
        var sawWordBody = false
        var sawExcelBody = false
        var sawPowerPointBody = false
        var odfMimeType: String? = null
        var entriesSeen = 0

        try {
            ZipInputStream(source.open().buffered()).use { zip ->
                while (entriesSeen < MAX_ZIP_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    entriesSeen++
                    val name = entry.name

                    // Authoritative when present: the OOXML part manifest.
                    if (name.equals("[Content_Types].xml", ignoreCase = true)) {
                        val xml = zip.readBoundedText(MAX_CONTENT_TYPES_BYTES)
                        fromContentTypes(xml)?.let { return it }
                    }

                    // ODF puts an uncompressed `mimetype` entry first.
                    if (name == "mimetype" && odfMimeType == null) {
                        odfMimeType = zip.readBoundedText(256).trim()
                    }

                    when (name) {
                        "word/document.xml" -> sawWordBody = true
                        "xl/workbook.xml" -> sawExcelBody = true
                        "ppt/presentation.xml" -> sawPowerPointBody = true
                    }
                }
            }
        } catch (e: Exception) {
            return fallbackToExtension(fileName, "the ZIP container could not be read: ${e.message}")
        }

        odfMimeType?.let { mime ->
            when {
                mime.startsWith("application/vnd.oasis.opendocument.text") ->
                    return Detection(DocumentFormat.ODT, Confidence.CONTENT, "ODF mimetype entry")
                mime.startsWith("application/vnd.oasis.opendocument.spreadsheet") ->
                    return Detection(DocumentFormat.ODS, Confidence.CONTENT, "ODF mimetype entry")
            }
        }

        // No manifest, but the body part gives it away.
        when {
            sawWordBody -> return Detection(
                DocumentFormat.DOCX, Confidence.CONTENT,
                "word/document.xml present but no [Content_Types].xml",
            )
            sawExcelBody -> return Detection(
                DocumentFormat.XLSX, Confidence.CONTENT,
                "xl/workbook.xml present but no [Content_Types].xml",
            )
            sawPowerPointBody -> return Detection(
                DocumentFormat.PPTX, Confidence.CONTENT,
                "ppt/presentation.xml present but no [Content_Types].xml",
            )
        }

        return Detection(DocumentFormat.ZIP, Confidence.CONTENT, "a ZIP archive with no Office parts")
    }

    /**
     * Maps the main-document content type declared in `[Content_Types].xml`.
     *
     * Template types (.dotx/.xltx) are mapped onto their document equivalents:
     * they render identically and the distinction only matters when authoring.
     */
    private fun fromContentTypes(xml: String): Detection? {
        fun hit(marker: String, format: DocumentFormat) =
            if (xml.contains(marker, ignoreCase = true)) {
                Detection(format, Confidence.CONTENT, "[Content_Types].xml declares $marker")
            } else {
                null
            }

        // Macro-enabled parts are checked first, and note that they are *not*
        // namespaced under wordprocessingml/spreadsheetml — Microsoft gave them
        // vnd.ms-word / vnd.ms-excel content types instead.
        return hit("ms-word.document.macroEnabled.main+xml", DocumentFormat.DOCM)
            ?: hit("ms-word.template.macroEnabledTemplate.main+xml", DocumentFormat.DOCM)
            ?: hit("ms-excel.sheet.macroEnabled.main+xml", DocumentFormat.XLSM)
            ?: hit("ms-excel.template.macroEnabled.main+xml", DocumentFormat.XLSM)
            ?: hit("ms-powerpoint.presentation.macroEnabled.main+xml", DocumentFormat.PPTX)
            ?: hit("wordprocessingml.document.main+xml", DocumentFormat.DOCX)
            ?: hit("wordprocessingml.template.main+xml", DocumentFormat.DOCX)
            ?: hit("spreadsheetml.sheet.main+xml", DocumentFormat.XLSX)
            ?: hit("spreadsheetml.template.main+xml", DocumentFormat.XLSX)
            ?: hit("presentationml.presentation.main+xml", DocumentFormat.PPTX)
            ?: hit("presentationml.template.main+xml", DocumentFormat.PPTX)
    }

    // ---- OLE2 / Compound File ----------------------------------------------

    /**
     * Tells legacy Office files apart, and separates them from *encrypted*
     * OOXML — which is an OLE container too, and would otherwise be reported as
     * a Word 97 file that we simply refuse to open.
     *
     * Stream names live in the compound-file directory as UTF-16LE, so we scan a
     * bounded window for them. This is a heuristic: the directory is normally
     * near the start, but a large file could place it beyond the window, in
     * which case we fall back to the generic legacy answer.
     */
    private fun detectOle(source: ByteSource, fileName: String?): Detection {
        val scan = try {
            source.readHeader(OLE_SCAN_BYTES)
        } catch (e: Exception) {
            return fallbackToExtension(fileName, "could not read the OLE container: ${e.message}")
        }

        fun containsStream(streamName: String): Boolean =
            scan.indexOf(streamName.toByteArray(Charsets.UTF_16LE)) >= 0

        if (containsStream("EncryptedPackage") || containsStream("EncryptionInfo")) {
            return Detection(
                DocumentFormat.ENCRYPTED_OFFICE, Confidence.CONTENT,
                "an OLE container holding an encrypted Office package",
            )
        }

        return when {
            containsStream("WordDocument") ->
                Detection(DocumentFormat.LEGACY_DOC, Confidence.CONTENT, "OLE WordDocument stream")
            containsStream("Workbook") || containsStream("Book") ->
                Detection(DocumentFormat.LEGACY_XLS, Confidence.CONTENT, "OLE Workbook stream")
            containsStream("PowerPoint Document") ->
                Detection(DocumentFormat.LEGACY_PPT, Confidence.CONTENT, "OLE PowerPoint stream")
            else -> {
                val hinted = DocumentFormat.fromExtension(fileName)
                if (hinted != null && hinted.isLegacyOffice) {
                    Detection(hinted, Confidence.EXTENSION, "an OLE container; type taken from the filename")
                } else {
                    Detection(
                        DocumentFormat.LEGACY_OFFICE, Confidence.CONTENT,
                        "an OLE container with no recognised Office stream",
                    )
                }
            }
        }
    }

    // ---- Google Drive shortcuts --------------------------------------------

    private val GOOGLE_HOSTS = listOf("docs.google.com", "drive.google.com", "sheets.google.com")

    /**
     * A `.gdoc` or `.gsheet` holds no document content whatsoever — it is a
     * few hundred bytes of JSON pointing at Drive. Detecting it explicitly is
     * what lets the app offer "open in Drive" rather than reporting a corrupt
     * file, which is what every naive viewer does with these.
     */
    private fun detectGoogleShortcut(header: ByteArray): Detection? {
        val text = header.decodeToString(0, minOf(header.size, 8192))
        if (text.trimStart().firstOrNull() != '{') return null
        if (!text.contains("\"url\"")) return null
        val host = GOOGLE_HOSTS.firstOrNull { text.contains(it) } ?: return null
        return Detection(
            DocumentFormat.GOOGLE_SHORTCUT, Confidence.CONTENT,
            "a JSON Drive shortcut pointing at $host",
        )
    }

    /** Extracts the Drive URL from a shortcut stub, for handing off to Drive. */
    fun googleShortcutUrl(source: ByteSource): String? {
        val text = try {
            source.readHeader(8192).decodeToString()
        } catch (e: Exception) {
            return null
        }
        val match = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(text) ?: return null
        return match.groupValues[1].replace("\\/", "/").ifBlank { null }
    }

    // ---- fallbacks ----------------------------------------------------------

    private fun fallbackToExtension(fileName: String?, why: String): Detection {
        val hinted = DocumentFormat.fromExtension(fileName)
        return if (hinted != null) {
            Detection(hinted, Confidence.EXTENSION, "$why; type taken from the filename")
        } else {
            Detection(DocumentFormat.UNKNOWN, Confidence.NONE, why)
        }
    }

    /** Reads at most [limit] bytes and decodes once, so multi-byte characters never straddle a chunk. */
    private fun InputStream.readBoundedText(limit: Int): String {
        val buffer = ByteArray(minOf(limit, 32 * 1024))
        val collected = java.io.ByteArrayOutputStream()
        var total = 0
        while (total < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - total))
            if (read <= 0) break
            collected.write(buffer, 0, read)
            total += read
        }
        return collected.toString(Charsets.UTF_8.name())
    }
}
