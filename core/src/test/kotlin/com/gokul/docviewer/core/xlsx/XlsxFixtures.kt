package com.gokul.docviewer.core.xlsx

import com.gokul.docviewer.core.Fixtures
import org.xmlpull.v1.XmlPullParserFactory

/** kxml2 stands in for the parser Android supplies at runtime. */
val TestParsers = XmlParserFactory {
    XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
}

/**
 * Assembles minimal but structurally real XLSX files — the same parts, paths
 * and relationships Excel writes, just with far less in them.
 */
object XlsxFixtures {

    fun workbookXml(vararg sheetNames: String, date1904: Boolean = false): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (date1904) append("""<workbookPr date1904="1"/>""")
        append("<sheets>")
        sheetNames.forEachIndexed { i, name ->
            append("""<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    fun relsXml(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        repeat(count) { i ->
            append("""<Relationship Id="rId${i + 1}" """)
            append("""Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """)
            append("""Target="worksheets/sheet${i + 1}.xml"/>""")
        }
        append("</Relationships>")
    }

    fun sharedStringsXml(vararg strings: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""count="${strings.size}" uniqueCount="${strings.size}">""")
        strings.forEach { append("<si><t>${escape(it)}</t></si>") }
        append("</sst>")
    }

    /** Each entry is one `cellXfs` record's numFmtId, in order. */
    fun stylesXml(numFmtIds: List<Int>, customFormats: Map<Int, String> = emptyMap()): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            if (customFormats.isNotEmpty()) {
                append("<numFmts>")
                customFormats.forEach { (id, code) ->
                    append("""<numFmt numFmtId="$id" formatCode="${escape(code)}"/>""")
                }
                append("</numFmts>")
            }
            append("""<cellXfs count="${numFmtIds.size}">""")
            numFmtIds.forEach { append("""<xf numFmtId="$it" fontId="0" fillId="0" borderId="0"/>""") }
            append("</cellXfs></styleSheet>")
        }

    /** `rows[rowIndex]` maps a cell reference to its raw `<c>` inner markup. */
    fun sheetXml(cells: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("<sheetData>")
        append(cells)
        append("</sheetData></worksheet>")
    }

    fun row(index: Int, vararg cells: String): String =
        """<row r="$index">${cells.joinToString("")}</row>"""

    fun numberCell(ref: String, value: String, style: Int? = null): String =
        """<c r="$ref"${style?.let { " s=\"$it\"" } ?: ""}><v>$value</v></c>"""

    fun sharedCell(ref: String, index: Int): String =
        """<c r="$ref" t="s"><v>$index</v></c>"""

    fun inlineCell(ref: String, text: String): String =
        """<c r="$ref" t="inlineStr"><is><t>${escape(text)}</t></is></c>"""

    fun boolCell(ref: String, value: Boolean): String =
        """<c r="$ref" t="b"><v>${if (value) 1 else 0}</v></c>"""

    fun errorCell(ref: String, code: String): String =
        """<c r="$ref" t="e"><v>${escape(code)}</v></c>"""

    fun formulaCell(ref: String, formula: String, cached: String): String =
        """<c r="$ref"><f>${escape(formula)}</f><v>$cached</v></c>"""

    /** A complete, single-sheet workbook. */
    fun workbook(
        sheetXml: String,
        sheetName: String = "Sheet1",
        sharedStrings: List<String> = emptyList(),
        styles: List<Int> = emptyList(),
        customFormats: Map<Int, String> = emptyMap(),
        date1904: Boolean = false,
    ): ByteArray {
        val entries = mutableListOf(
            "[Content_Types].xml" to contentTypes(),
            "xl/workbook.xml" to workbookXml(sheetName, date1904 = date1904),
            "xl/_rels/workbook.xml.rels" to relsXml(1),
            "xl/worksheets/sheet1.xml" to sheetXml,
        )
        if (sharedStrings.isNotEmpty()) {
            entries += "xl/sharedStrings.xml" to sharedStringsXml(*sharedStrings.toTypedArray())
        }
        if (styles.isNotEmpty()) {
            entries += "xl/styles.xml" to stylesXml(styles, customFormats)
        }
        return Fixtures.zip(*entries.toTypedArray())
    }

    fun multiSheetWorkbook(sheets: List<Pair<String, String>>): ByteArray {
        val entries = mutableListOf(
            "[Content_Types].xml" to contentTypes(),
            "xl/workbook.xml" to workbookXml(*sheets.map { it.first }.toTypedArray()),
            "xl/_rels/workbook.xml.rels" to relsXml(sheets.size),
        )
        sheets.forEachIndexed { i, (_, body) ->
            entries += "xl/worksheets/sheet${i + 1}.xml" to body
        }
        return Fixtures.zip(*entries.toTypedArray())
    }

    private fun contentTypes(): String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            "</Types>"

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
