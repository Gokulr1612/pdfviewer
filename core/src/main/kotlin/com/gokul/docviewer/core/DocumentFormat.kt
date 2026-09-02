package com.gokul.docviewer.core

/**
 * Every file type the app can recognise — including the ones it deliberately
 * does not render.
 *
 * Recognising a format we cannot open is a feature, not an oversight: it lets
 * the UI say "this is a Word 97 document, which this app can't show" instead of
 * failing with a parse error the user can do nothing about.
 */
enum class DocumentFormat(
    /** Whether a viewer exists for this format today. */
    val isSupported: Boolean,
    /** Short name for the UI. */
    val label: String,
    /** Canonical extension, without the dot. Null where several apply. */
    val extension: String?,
    /** Canonical MIME type, for share/print intents. */
    val mimeType: String,
) {
    PDF(true, "PDF", "pdf", "application/pdf"),

    DOCX(
        true, "Word document", "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ),
    XLSX(
        true, "Excel workbook", "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ),

    /** Macro-enabled OOXML. Same body format; we render it and ignore macros. */
    DOCM(
        true, "Word document (macro-enabled)", "docm",
        "application/vnd.ms-word.document.macroEnabled.12",
    ),
    XLSM(
        true, "Excel workbook (macro-enabled)", "xlsm",
        "application/vnd.ms-excel.sheet.macroEnabled.12",
    ),

    /** Recognised, deferred to a later phase. */
    PPTX(
        false, "PowerPoint presentation", "pptx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ),

    /**
     * Pre-2007 binary Office files. A completely different container from
     * OOXML — deliberately out of scope. We can often tell it is a legacy
     * Office file but not always which application wrote it.
     */
    LEGACY_DOC(false, "Word 97–2003 document", "doc", "application/msword"),
    LEGACY_XLS(false, "Excel 97–2003 workbook", "xls", "application/vnd.ms-excel"),
    LEGACY_PPT(false, "PowerPoint 97–2003", "ppt", "application/vnd.ms-powerpoint"),
    LEGACY_OFFICE(false, "Office 97–2003 file", null, "application/x-ole-storage"),

    /** OpenDocument, as produced by LibreOffice and Google's ODF export. */
    ODT(false, "OpenDocument text", "odt", "application/vnd.oasis.opendocument.text"),
    ODS(
        false, "OpenDocument spreadsheet", "ods",
        "application/vnd.oasis.opendocument.spreadsheet",
    ),

    /**
     * A Google Docs/Sheets/Slides *shortcut*, not a document. These contain no
     * content at all — just a JSON stub pointing at Drive. See
     * [FormatDetector] for why this matters.
     */
    GOOGLE_SHORTCUT(false, "Google Drive shortcut", null, "application/json"),

    /**
     * A password-protected OOXML file. Confusingly these are *not* ZIPs — the
     * encrypted package is wrapped in an OLE container — so they must be told
     * apart from genuine legacy Office files.
     */
    ENCRYPTED_OFFICE(false, "Password-protected document", null, "application/x-tika-ooxml-protected"),

    RTF(false, "Rich Text document", "rtf", "application/rtf"),
    CSV(false, "CSV file", "csv", "text/csv"),

    /** A ZIP archive that is not an Office document. */
    ZIP(false, "ZIP archive", "zip", "application/zip"),

    /** Read successfully, but nothing matched. */
    UNKNOWN(false, "Unrecognised file", null, "application/octet-stream"),
    ;

    val isOoxml: Boolean
        get() = this == DOCX || this == XLSX || this == DOCM || this == XLSM || this == PPTX

    val isLegacyOffice: Boolean
        get() = this == LEGACY_DOC || this == LEGACY_XLS ||
            this == LEGACY_PPT || this == LEGACY_OFFICE

    companion object {
        /**
         * Best guess from a filename alone. Only ever used as a tie-breaker —
         * see [FormatDetector], which trusts file content first.
         */
        fun fromExtension(name: String?): DocumentFormat? {
            val ext = name?.substringAfterLast('.', "")?.lowercase()
            if (ext.isNullOrEmpty()) return null
            return when (ext) {
                "pdf" -> PDF
                "docx" -> DOCX
                "docm" -> DOCM
                "xlsx" -> XLSX
                "xlsm" -> XLSM
                "pptx" -> PPTX
                "doc" -> LEGACY_DOC
                "xls" -> LEGACY_XLS
                "ppt" -> LEGACY_PPT
                "odt" -> ODT
                "ods" -> ODS
                "rtf" -> RTF
                "csv" -> CSV
                "zip" -> ZIP
                "gdoc", "gsheet", "gslides" -> GOOGLE_SHORTCUT
                else -> null
            }
        }
    }
}
