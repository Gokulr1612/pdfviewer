package com.gokul.docviewer.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class FormatDetectorTest {

    private fun detect(bytes: ByteArray, name: String? = null) =
        FormatDetector.detect(bytes.asSource(), name)

    // ---- PDF ---------------------------------------------------------------

    @Test
    fun `recognises a PDF`() {
        val result = detect(Fixtures.pdf())
        assertEquals(DocumentFormat.PDF, result.format)
        assertEquals(Confidence.CONTENT, result.confidence)
    }

    @Test
    fun `recognises a PDF with leading junk before the header`() {
        val result = detect(Fixtures.pdf(leadingJunk = 200))
        assertEquals(DocumentFormat.PDF, result.format)
        assertTrue(result.reason.contains("offset 200"), "reason should record the offset: ${result.reason}")
    }

    @Test
    fun `ignores a PDF header that appears beyond the search window`() {
        val result = detect(Fixtures.pdf(leadingJunk = 4000))
        assertEquals(DocumentFormat.UNKNOWN, result.format)
    }

    // ---- OOXML -------------------------------------------------------------

    @Test
    fun `recognises DOCX from the content types manifest`() {
        assertEquals(DocumentFormat.DOCX, detect(Fixtures.docx()).format)
    }

    @Test
    fun `recognises XLSX from the content types manifest`() {
        assertEquals(DocumentFormat.XLSX, detect(Fixtures.xlsx()).format)
    }

    @Test
    fun `separates macro-enabled OOXML from the plain variants`() {
        assertEquals(DocumentFormat.DOCM, detect(Fixtures.docm()).format)
        assertEquals(DocumentFormat.XLSM, detect(Fixtures.xlsm()).format)
    }

    @Test
    fun `maps OOXML templates onto their document equivalents`() {
        assertEquals(DocumentFormat.DOCX, detect(Fixtures.dotx()).format)
        assertEquals(DocumentFormat.DOCM, detect(Fixtures.dotm()).format)
    }

    @Test
    fun `identifies macro-enabled files from the manifest, not the body part`() {
        // Regression: the macro-enabled content types are namespaced under
        // vnd.ms-word / vnd.ms-excel rather than wordprocessingml, so matching
        // on the wrong string silently fell through to the body-part fallback
        // and reported every .docm as a .docx.
        assertTrue(detect(Fixtures.docm()).reason.contains("[Content_Types].xml"))
        assertTrue(detect(Fixtures.xlsm()).reason.contains("[Content_Types].xml"))
    }

    @Test
    fun `recognises PPTX but reports it as unsupported`() {
        val result = detect(Fixtures.pptx())
        assertEquals(DocumentFormat.PPTX, result.format)
        assertTrue(!result.isSupported, "PPTX is deferred to a later phase")
    }

    @Test
    fun `falls back to the body part when the manifest is missing`() {
        val result = detect(Fixtures.docxWithoutManifest())
        assertEquals(DocumentFormat.DOCX, result.format)
        assertEquals(Confidence.CONTENT, result.confidence)
    }

    // ---- OpenDocument and plain archives ------------------------------------

    @Test
    fun `recognises OpenDocument files`() {
        assertEquals(DocumentFormat.ODT, detect(Fixtures.odt()).format)
        assertEquals(DocumentFormat.ODS, detect(Fixtures.ods()).format)
    }

    @Test
    fun `does not mistake an ordinary ZIP for an Office document`() {
        assertEquals(DocumentFormat.ZIP, detect(Fixtures.plainZip()).format)
    }

    // ---- legacy and encrypted Office ----------------------------------------

    @Test
    fun `tells legacy Word and Excel apart inside an OLE container`() {
        assertEquals(DocumentFormat.LEGACY_DOC, detect(Fixtures.ole("WordDocument")).format)
        assertEquals(DocumentFormat.LEGACY_XLS, detect(Fixtures.ole("Workbook")).format)
        assertEquals(DocumentFormat.LEGACY_PPT, detect(Fixtures.ole("PowerPoint Document")).format)
    }

    @Test
    fun `reports an encrypted OOXML file as protected, not as a legacy file`() {
        // An encrypted .docx is an OLE container, so without this it would be
        // reported as an unopenable Word 97 document and the user would never
        // be asked for the password.
        val result = detect(Fixtures.ole("EncryptedPackage", "EncryptionInfo"), "budget.docx")
        assertEquals(DocumentFormat.ENCRYPTED_OFFICE, result.format)
    }

    @Test
    fun `uses the filename for an OLE container it cannot identify`() {
        val result = detect(Fixtures.ole("SomethingElse"), "report.doc")
        assertEquals(DocumentFormat.LEGACY_DOC, result.format)
        assertEquals(Confidence.EXTENSION, result.confidence)
    }

    // ---- Google Drive shortcuts ---------------------------------------------

    @Test
    fun `recognises a Google Drive shortcut, which holds no document at all`() {
        val result = detect(Fixtures.googleShortcut(), "Quarterly plan.gdoc")
        assertEquals(DocumentFormat.GOOGLE_SHORTCUT, result.format)
        assertEquals(Confidence.CONTENT, result.confidence)
    }

    @Test
    fun `extracts the Drive URL from a shortcut so it can be handed off`() {
        val url = FormatDetector.googleShortcutUrl(Fixtures.googleShortcut().asSource())
        assertEquals("https://docs.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz", url)
    }

    @Test
    fun `does not treat arbitrary JSON as a Drive shortcut`() {
        val json = """{"url": "https://example.com/thing", "name": "not drive"}"""
        assertEquals(DocumentFormat.UNKNOWN, detect(json.toByteArray()).format)
        assertNull(FormatDetector.googleShortcutUrl("{}".toByteArray().asSource()))
    }

    // ---- the whole point ----------------------------------------------------

    @Test
    fun `content beats a filename that lies`() {
        // Sharing apps rename and mislabel files constantly. A PDF called .docx
        // must open as a PDF, and a DOCX called .pdf must open as a DOCX.
        assertEquals(DocumentFormat.PDF, detect(Fixtures.pdf(), "invoice.docx").format)
        assertEquals(DocumentFormat.DOCX, detect(Fixtures.docx(), "invoice.pdf").format)
        assertEquals(DocumentFormat.XLSX, detect(Fixtures.xlsx(), "data.bin").format)
    }

    @Test
    fun `falls back to the filename only when the bytes say nothing`() {
        val result = detect(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), "mystery.docx")
        assertEquals(DocumentFormat.DOCX, result.format)
        assertEquals(Confidence.EXTENSION, result.confidence)
    }

    @Test
    fun `handles an empty file without throwing`() {
        val result = detect(ByteArray(0), "empty.pdf")
        assertEquals(DocumentFormat.PDF, result.format)
        assertEquals(Confidence.EXTENSION, result.confidence)
        assertTrue(result.reason.contains("empty"))
    }

    @Test
    fun `reports an unreadable source instead of crashing`() {
        val exploding = ByteSource { throw java.io.IOException("permission revoked") }
        val result = FormatDetector.detect(exploding, "gone.pdf")
        assertEquals(DocumentFormat.PDF, result.format)
        assertEquals(Confidence.EXTENSION, result.confidence)
    }

    @Test
    fun `recognises RTF`() {
        assertEquals(DocumentFormat.RTF, detect(Fixtures.rtf()).format)
    }

    @Test
    fun `knows nothing about a file with neither signature nor extension`() {
        val result = detect(ByteArray(64) { 0x7F })
        assertEquals(DocumentFormat.UNKNOWN, result.format)
        assertEquals(Confidence.NONE, result.confidence)
    }
}
