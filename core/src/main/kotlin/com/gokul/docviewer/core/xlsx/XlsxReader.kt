package com.gokul.docviewer.core.xlsx

import com.gokul.docviewer.core.ByteSource
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Reads XLSX workbooks.
 *
 * An XLSX file is a ZIP of XML, so this needs nothing beyond a ZIP reader and a
 * pull parser — both of which Android already bundles. That is the reason not
 * to reach for Apache POI here: POI's method count strains the dex limit and
 * the lighter alternatives depend on StAX, which Android does not ship.
 *
 * Reading happens in two passes over the archive. The first collects the small
 * shared parts — the sheet list, the string table, the style records — and the
 * second streams whichever sheet was asked for. Sheet bodies are by far the
 * largest part of a workbook, so they are never held alongside one another.
 */
class XlsxReader(
    private val parsers: XmlParserFactory,
    private val limits: ReadLimits = ReadLimits(),
) {

    /** Reads the workbook's structure without parsing any sheet body. */
    fun readIndex(source: ByteSource): WorkbookIndex {
        var workbookXml: ByteArray? = null
        var relsXml: ByteArray? = null
        var sharedStringsXml: ByteArray? = null
        var stylesXml: ByteArray? = null

        ZipInputStream(source.open().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "xl/workbook.xml" -> workbookXml = zip.readBytes()
                    "xl/_rels/workbook.xml.rels" -> relsXml = zip.readBytes()
                    "xl/sharedStrings.xml" -> sharedStringsXml = zip.readBytes()
                    "xl/styles.xml" -> stylesXml = zip.readBytes()
                }
            }
        }

        val workbook = workbookXml
            ?: throw XlsxFormatException("This file has no xl/workbook.xml, so it is not a workbook.")

        val relations = relsXml?.let(::parseRelationships).orEmpty()
        val (sheets, epoch1904) = parseWorkbook(workbook, relations)

        return WorkbookIndex(
            sheets = sheets,
            sharedStrings = sharedStringsXml?.let(::parseSharedStrings).orEmpty(),
            dateStyles = stylesXml?.let(::parseDateStyles).orEmpty(),
            epoch1904 = epoch1904,
        )
    }

    /** Streams one sheet's cells. [sheetIndex] indexes [WorkbookIndex.sheets]. */
    fun readSheet(source: ByteSource, index: WorkbookIndex, sheetIndex: Int): Sheet {
        val ref = index.sheets.getOrNull(sheetIndex)
            ?: throw XlsxFormatException("This workbook has no sheet at position $sheetIndex.")

        ZipInputStream(source.open().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == ref.path) {
                    return parseSheet(zip, ref.name, index)
                }
            }
        }
        throw XlsxFormatException("The workbook lists a sheet at ${ref.path} but the file has no such part.")
    }

    // ---- workbook.xml -------------------------------------------------------

    private fun parseWorkbook(
        bytes: ByteArray,
        relations: Map<String, String>,
    ): Pair<List<SheetRef>, Boolean> {
        val sheets = mutableListOf<SheetRef>()
        var epoch1904 = false
        var fallbackIndex = 1

        parse(bytes) { parser ->
            parser.forEachChild { top ->
                when (top) {
                    "workbookPr" -> {
                        val flag = parser.attr("date1904") ?: parser.attr("dateCompatibility")
                        epoch1904 = flag == "1" || flag == "true"
                    }
                    "sheets" -> parser.forEachChild { child ->
                        if (child != "sheet") return@forEachChild
                        val name = parser.attr("name") ?: "Sheet${sheets.size + 1}"
                        val relationId = parser.attr("id")
                            ?: parser.getAttributeValue(R_NAMESPACE, "id")
                        val target = relationId?.let { relations[it] }
                            // Some producers omit the relationship; the
                            // conventional path is right often enough to try.
                            ?: "worksheets/sheet${fallbackIndex}.xml"
                        fallbackIndex++
                        sheets += SheetRef(name = name, path = normalisePart(target))
                    }
                }
            }
        }
        return sheets to epoch1904
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val relations = mutableMapOf<String, String>()
        parse(bytes) { parser ->
            parser.forEachChild { child ->
                if (child == "Relationship") {
                    val id = parser.attr("Id")
                    val target = parser.attr("Target")
                    if (id != null && target != null) relations[id] = target
                }
            }
        }
        return relations
    }

    /** Relationship targets are relative to `xl/`, and may be absolute. */
    private fun normalisePart(target: String): String {
        val cleaned = target.removePrefix("/")
        return if (cleaned.startsWith("xl/")) cleaned else "xl/$cleaned"
    }

    // ---- sharedStrings.xml --------------------------------------------------

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = ArrayList<String>()
        parse(bytes) { parser ->
            parser.forEachChild { child ->
                if (child != "si" || strings.size >= limits.maxSharedStrings) return@forEachChild
                // A shared string is either a single <t>, or several <r> runs
                // each holding one; both flatten to the same plain text.
                val text = StringBuilder()
                parser.forEachChild { part ->
                    when (part) {
                        "t" -> text.append(parser.readElementText())
                        "r" -> parser.forEachChild { runPart ->
                            if (runPart == "t") text.append(parser.readElementText())
                        }
                    }
                }
                strings += text.toString()
            }
        }
        return strings
    }

    // ---- styles.xml ---------------------------------------------------------

    /**
     * Collects the `cellXfs` indices whose number format is a date, which is
     * the only way to know that `45322` should be shown as a date rather than
     * as the integer it is stored as.
     */
    private fun parseDateStyles(bytes: ByteArray): Set<Int> {
        val customFormats = mutableMapOf<Int, String>()
        val dateStyles = mutableSetOf<Int>()

        parse(bytes) { parser ->
            parser.forEachChild { top ->
                when (top) {
                    "numFmts" -> parser.forEachChild { child ->
                        if (child != "numFmt") return@forEachChild
                        val id = parser.attr("numFmtId")?.toIntOrNull()
                        val code = parser.attr("formatCode")
                        if (id != null && code != null) customFormats[id] = code
                    }
                    "cellXfs" -> {
                        var styleIndex = 0
                        parser.forEachChild { child ->
                            if (child != "xf") return@forEachChild
                            val numFmtId = parser.attr("numFmtId")?.toIntOrNull() ?: 0
                            if (ExcelDates.isDateFormat(numFmtId, customFormats[numFmtId])) {
                                dateStyles += styleIndex
                            }
                            styleIndex++
                        }
                    }
                }
            }
        }
        return dateStyles
    }

    // ---- worksheet ----------------------------------------------------------

    private fun parseSheet(stream: InputStream, name: String, index: WorkbookIndex): Sheet {
        val rows = ArrayList<Row>()
        var widestColumn = -1
        var truncated = false
        // Tracks the row number for files that omit the `r` attribute.
        var impliedRow = 0

        parse(stream) { parser ->
            parser.forEachChild { top ->
                if (top != "sheetData") return@forEachChild
                parser.forEachChild { rowTag ->
                    if (rowTag != "row") return@forEachChild
                    if (rows.size >= limits.maxRows) {
                        truncated = true
                        return@forEachChild
                    }
                    val rowIndex = parser.attr("r")?.toIntOrNull()?.minus(1) ?: impliedRow
                    impliedRow = rowIndex + 1

                    val cells = ArrayList<Cell>()
                    var impliedColumn = 0
                    parser.forEachChild { cellTag ->
                        if (cellTag != "c") return@forEachChild
                        val reference = parser.attr("r")
                        val column = reference?.let { CellRef.parse(it)?.first } ?: impliedColumn
                        impliedColumn = column + 1
                        if (column >= limits.maxColumns) {
                            truncated = true
                            return@forEachChild
                        }
                        val value = readCell(parser, index)
                        if (value != null) {
                            cells += Cell(column, value)
                            if (column > widestColumn) widestColumn = column
                        }
                    }
                    if (cells.isNotEmpty()) rows += Row(rowIndex, cells)
                }
            }
        }

        val lastRow = rows.lastOrNull()?.index ?: -1
        return Sheet(
            name = name,
            rows = rows,
            columnCount = widestColumn + 1,
            rowCount = lastRow + 1,
            truncated = truncated,
        )
    }

    /** Reads one `<c>`, resolving its type and style into a typed value. */
    private fun readCell(parser: XmlPullParser, index: WorkbookIndex): CellValue? {
        val type = parser.attr("t") ?: "n"
        val styleIndex = parser.attr("s")?.toIntOrNull()

        var raw: String? = null
        var inlineText: String? = null

        parser.forEachChild { child ->
            when (child) {
                "v" -> raw = parser.readElementText()
                "is" -> {
                    val text = StringBuilder()
                    parser.forEachChild { part ->
                        when (part) {
                            "t" -> text.append(parser.readElementText())
                            "r" -> parser.forEachChild { runPart ->
                                if (runPart == "t") text.append(parser.readElementText())
                            }
                        }
                    }
                    inlineText = text.toString()
                }
                // `f` holds the formula; `v` alongside it holds the cached
                // result, which is what a viewer should show.
            }
        }

        return when (type) {
            "s" -> raw?.toIntOrNull()
                ?.let { index.sharedStrings.getOrNull(it) }
                ?.let { CellValue.Text(it) }
            "inlineStr" -> inlineText?.let { CellValue.Text(it) }
            "str" -> raw?.let { CellValue.Text(it) }
            "b" -> raw?.let { CellValue.Bool(it == "1" || it.equals("true", ignoreCase = true)) }
            "e" -> raw?.let { CellValue.ErrorValue(it) }
            else -> raw?.toDoubleOrNull()?.let { number ->
                if (styleIndex != null && styleIndex in index.dateStyles) {
                    CellValue.DateTime(number)
                } else {
                    CellValue.Number(number)
                }
            }
        }
    }

    // ---- plumbing -----------------------------------------------------------

    private inline fun parse(bytes: ByteArray, block: (XmlPullParser) -> Unit) =
        parse(bytes.inputStream(), block)

    private inline fun parse(stream: InputStream, block: (XmlPullParser) -> Unit) {
        val parser = parsers.newParser()
        parser.setInput(stream, null)
        // Advance onto the root element so callers walk its children rather
        // than the root itself. Stepping manually instead of using nextTag()
        // tolerates comments and processing instructions before the root.
        while (parser.eventType != XmlPullParser.START_TAG &&
            parser.eventType != XmlPullParser.END_DOCUMENT
        ) {
            parser.next()
        }
        block(parser)
    }

    private companion object {
        const val R_NAMESPACE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    }
}

class XlsxFormatException(message: String) : Exception(message)

/** How a cell should read in a grid. */
fun CellValue.displayText(epoch1904: Boolean): String = when (this) {
    is CellValue.Text -> value
    is CellValue.Number -> ExcelDates.formatNumber(value)
    is CellValue.DateTime -> ExcelDates.format(serial, epoch1904)
    is CellValue.Bool -> if (value) "TRUE" else "FALSE"
    is CellValue.ErrorValue -> code
}
