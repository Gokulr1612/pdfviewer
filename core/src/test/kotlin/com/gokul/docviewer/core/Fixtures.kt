package com.gokul.docviewer.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builders for the smallest files that still look real to a detector. */
object Fixtures {

    fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun contentTypes(vararg overrides: Pair<String, String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        overrides.forEach { (part, type) ->
            append("""<Override PartName="$part" ContentType="$type"/>""")
        }
        append("</Types>")
    }

    private const val OOXML = "application/vnd.openxmlformats-officedocument"

    fun docx(): ByteArray = zip(
        "[Content_Types].xml" to contentTypes(
            "/word/document.xml" to "$OOXML.wordprocessingml.document.main+xml",
        ),
        "word/document.xml" to "<w:document/>",
    )

    fun docm(): ByteArray = zip(
        "[Content_Types].xml" to contentTypes(
            "/word/document.xml" to "application/vnd.ms-word.document.macroEnabled.main+xml",
        ),
        "word/document.xml" to "<w:document/>",
    )

    fun dotm(): ByteArray = zip(
        "[Content_Types].xml" to contentTypes(
            "/word/document.xml" to "application/vnd.ms-word.template.macroEnabledTemplate.main+xml",
        ),
        "word/document.xml" to "<w:document/>",
    )

    fun dotx(): ByteArray = zip(
        "[Content_Types].xml" to contentTypes(
            "/word/document.xml" to "$OOXML.wordprocessingml.template.main+xml",
        ),
        "word/document.xml" to "<w:document/>",
    )

    fun xlsx(): ByteArray = zip(
        "[Content_Types].xml" to contentTypes(
            "/xl/workbook.xml" to "$OOXML.spreadsheetml.sheet.main+xml",
        ),
        "xl/workbook.xml" to "<workbook/>",
    )

    fun xlsm(): ByteArray = zip(
        "[Content_Types].xml" to contentTypes(
            "/xl/workbook.xml" to "application/vnd.ms-excel.sheet.macroEnabled.main+xml",
        ),
        "xl/workbook.xml" to "<workbook/>",
    )

    fun pptx(): ByteArray = zip(
        "[Content_Types].xml" to contentTypes(
            "/ppt/presentation.xml" to "$OOXML.presentationml.presentation.main+xml",
        ),
        "ppt/presentation.xml" to "<presentation/>",
    )

    /** An OOXML file whose manifest is missing — detection must fall back to the body part. */
    fun docxWithoutManifest(): ByteArray = zip("word/document.xml" to "<w:document/>")

    fun odt(): ByteArray = zip(
        "mimetype" to "application/vnd.oasis.opendocument.text",
        "content.xml" to "<office:document-content/>",
    )

    fun ods(): ByteArray = zip(
        "mimetype" to "application/vnd.oasis.opendocument.spreadsheet",
        "content.xml" to "<office:document-content/>",
    )

    fun plainZip(): ByteArray = zip("notes.txt" to "hello", "photo.jpg" to "not really a jpg")

    fun pdf(leadingJunk: Int = 0): ByteArray {
        val body = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"
        return ByteArray(leadingJunk) { 0x20 } + body.toByteArray(Charsets.US_ASCII)
    }

    private val OLE_HEADER = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    /**
     * An OLE compound file carrying [streamNames] in its directory. Real files
     * store those names as UTF-16LE, which is what the detector scans for.
     */
    fun ole(vararg streamNames: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(OLE_HEADER)
        out.write(ByteArray(512 - OLE_HEADER.size))
        streamNames.forEach { name ->
            out.write(name.toByteArray(Charsets.UTF_16LE))
            out.write(ByteArray(64))
        }
        return out.toByteArray()
    }

    fun googleShortcut(kind: String = "document"): ByteArray = """
        {
          "url": "https://docs.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz",
          "doc_id": "1AbCdEfGhIjKlMnOpQrStUvWxYz",
          "email": "someone@example.com",
          "resource_id": "$kind:1AbCdEfGhIjKlMnOpQrStUvWxYz"
        }
    """.trimIndent().toByteArray(Charsets.UTF_8)

    fun rtf(): ByteArray = """{\rtf1\ansi\deff0 {\fonttbl}\f0 Hello.}""".toByteArray(Charsets.US_ASCII)
}

fun ByteArray.asSource(): ByteSource = ByteSource.of(this)
