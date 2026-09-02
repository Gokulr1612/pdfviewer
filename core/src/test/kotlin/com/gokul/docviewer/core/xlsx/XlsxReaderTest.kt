package com.gokul.docviewer.core.xlsx

import com.gokul.docviewer.core.asSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class XlsxReaderTest {

    private val reader = XlsxReader(TestParsers)

    private fun read(bytes: ByteArray, sheet: Int = 0): Pair<WorkbookIndex, Sheet> {
        val source = bytes.asSource()
        val index = reader.readIndex(source)
        return index to reader.readSheet(source, index, sheet)
    }

    // ---- structure ----------------------------------------------------------

    @Test
    fun `reads sheet names in workbook order`() {
        val bytes = XlsxFixtures.multiSheetWorkbook(
            listOf(
                "Summary" to XlsxFixtures.sheetXml(""),
                "Raw data" to XlsxFixtures.sheetXml(""),
                "Notes" to XlsxFixtures.sheetXml(""),
            ),
        )
        val index = reader.readIndex(bytes.asSource())
        assertEquals(listOf("Summary", "Raw data", "Notes"), index.sheets.map { it.name })
    }

    @Test
    fun `resolves each sheet to its own part through the relationships`() {
        val bytes = XlsxFixtures.multiSheetWorkbook(
            listOf(
                "First" to XlsxFixtures.sheetXml(XlsxFixtures.row(1, XlsxFixtures.numberCell("A1", "11"))),
                "Second" to XlsxFixtures.sheetXml(XlsxFixtures.row(1, XlsxFixtures.numberCell("A1", "22"))),
            ),
        )
        val source = bytes.asSource()
        val index = reader.readIndex(source)
        assertEquals(CellValue.Number(11.0), reader.readSheet(source, index, 0).cellAt(0, 0))
        assertEquals(CellValue.Number(22.0), reader.readSheet(source, index, 1).cellAt(0, 0))
    }

    @Test
    fun `rejects a file that is not a workbook`() {
        val notAWorkbook = com.gokul.docviewer.core.Fixtures.zip("hello.txt" to "hi")
        assertFailsWith<XlsxFormatException> { reader.readIndex(notAWorkbook.asSource()) }
    }

    // ---- cell types ---------------------------------------------------------

    @Test
    fun `reads shared strings, inline strings, numbers, booleans and errors`() {
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                XlsxFixtures.row(
                    1,
                    XlsxFixtures.sharedCell("A1", 0),
                    XlsxFixtures.sharedCell("B1", 1),
                    XlsxFixtures.inlineCell("C1", "inline text"),
                    XlsxFixtures.numberCell("D1", "42.5"),
                    XlsxFixtures.boolCell("E1", true),
                    XlsxFixtures.errorCell("F1", "#DIV/0!"),
                ),
            ),
            sharedStrings = listOf("Region", "Revenue"),
        )
        val (_, sheet) = read(bytes)
        assertEquals(CellValue.Text("Region"), sheet.cellAt(0, 0))
        assertEquals(CellValue.Text("Revenue"), sheet.cellAt(0, 1))
        assertEquals(CellValue.Text("inline text"), sheet.cellAt(0, 2))
        assertEquals(CellValue.Number(42.5), sheet.cellAt(0, 3))
        assertEquals(CellValue.Bool(true), sheet.cellAt(0, 4))
        assertEquals(CellValue.ErrorValue("#DIV/0!"), sheet.cellAt(0, 5))
    }

    @Test
    fun `shows a formula's cached result rather than the formula`() {
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                XlsxFixtures.row(1, XlsxFixtures.formulaCell("A1", "SUM(B1:B9)", "168")),
            ),
        )
        val (_, sheet) = read(bytes)
        assertEquals(CellValue.Number(168.0), sheet.cellAt(0, 0))
    }

    @Test
    fun `a shared string index pointing nowhere yields no cell rather than a crash`() {
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                XlsxFixtures.row(1, XlsxFixtures.sharedCell("A1", 99)),
            ),
            sharedStrings = listOf("only one"),
        )
        val (_, sheet) = read(bytes)
        assertEquals(null, sheet.cellAt(0, 0))
    }

    // ---- dates --------------------------------------------------------------

    @Test
    fun `treats a number as a date only when its style says so`() {
        // Style 0 is General, style 1 is built-in format 14 (a date).
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                XlsxFixtures.row(
                    1,
                    XlsxFixtures.numberCell("A1", "45322", style = 0),
                    XlsxFixtures.numberCell("B1", "45322", style = 1),
                ),
            ),
            styles = listOf(0, 14),
        )
        val (index, sheet) = read(bytes)
        assertEquals(CellValue.Number(45322.0), sheet.cellAt(0, 0))
        assertEquals(CellValue.DateTime(45322.0), sheet.cellAt(0, 1))
        assertEquals("2024-01-31", sheet.cellAt(0, 1)!!.displayText(index.epoch1904))
    }

    @Test
    fun `recognises a custom date format code`() {
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                XlsxFixtures.row(1, XlsxFixtures.numberCell("A1", "45322", style = 0)),
            ),
            styles = listOf(banner164()),
            customFormats = mapOf(banner164() to "dd/mm/yyyy"),
        )
        val (_, sheet) = read(bytes)
        assertTrue(sheet.cellAt(0, 0) is CellValue.DateTime)
    }

    @Test
    fun `does not mistake a currency format for a date`() {
        // The "m" here is a literal, not a month.
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                XlsxFixtures.row(1, XlsxFixtures.numberCell("A1", "1500", style = 0)),
            ),
            styles = listOf(banner164()),
            customFormats = mapOf(banner164() to "\"$\"#,##0"),
        )
        val (_, sheet) = read(bytes)
        assertEquals(CellValue.Number(1500.0), sheet.cellAt(0, 0))
    }

    @Test
    fun `honours the 1904 epoch used by classic Mac workbooks`() {
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                XlsxFixtures.row(1, XlsxFixtures.numberCell("A1", "0", style = 0)),
            ),
            styles = listOf(14),
            date1904 = true,
        )
        val (index, sheet) = read(bytes)
        assertTrue(index.epoch1904)
        assertEquals("1904-01-01", sheet.cellAt(0, 0)!!.displayText(index.epoch1904))
    }

    // ---- shape --------------------------------------------------------------

    @Test
    fun `keeps a sparse sheet sparse but reports its true extent`() {
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                XlsxFixtures.row(1, XlsxFixtures.numberCell("A1", "1")) +
                    XlsxFixtures.row(50, XlsxFixtures.numberCell("J50", "2")),
            ),
        )
        val (_, sheet) = read(bytes)
        assertEquals(2, sheet.rows.size, "only populated rows are materialised")
        assertEquals(50, sheet.rowCount)
        assertEquals(10, sheet.columnCount)
        assertEquals(CellValue.Number(2.0), sheet.cellAt(49, 9))
    }

    @Test
    fun `stops at the row limit and says that it did`() {
        val rows = (1..50).joinToString("") {
            XlsxFixtures.row(it, XlsxFixtures.numberCell("A$it", "$it"))
        }
        val limited = XlsxReader(TestParsers, ReadLimits(maxRows = 10))
        val source = XlsxFixtures.workbook(sheetXml = XlsxFixtures.sheetXml(rows)).asSource()
        val index = limited.readIndex(source)
        val sheet = limited.readSheet(source, index, 0)
        assertEquals(10, sheet.rows.size)
        assertTrue(sheet.truncated, "a truncated read must be reported, not silently short")
    }

    @Test
    fun `reads a sheet whose rows omit their row numbers`() {
        val bytes = XlsxFixtures.workbook(
            sheetXml = XlsxFixtures.sheetXml(
                "<row><c><v>1</v></c><c><v>2</v></c></row><row><c><v>3</v></c></row>",
            ),
        )
        val (_, sheet) = read(bytes)
        assertEquals(CellValue.Number(1.0), sheet.cellAt(0, 0))
        assertEquals(CellValue.Number(2.0), sheet.cellAt(0, 1))
        assertEquals(CellValue.Number(3.0), sheet.cellAt(1, 0))
    }

    private fun banner164() = 164
}
