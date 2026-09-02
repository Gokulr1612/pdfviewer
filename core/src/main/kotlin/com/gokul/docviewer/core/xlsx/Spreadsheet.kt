package com.gokul.docviewer.core.xlsx

/** A single cell's typed value. */
sealed interface CellValue {
    data class Text(val value: String) : CellValue
    data class Number(val value: Double) : CellValue

    /**
     * A number the workbook's styles say should be read as a date or time.
     * The serial is kept rather than a resolved date so the epoch stays an
     * explicit part of the conversion — see [ExcelDates].
     */
    data class DateTime(val serial: Double) : CellValue
    data class Bool(val value: Boolean) : CellValue

    /** `#DIV/0!` and friends. Kept so the grid shows what Excel would show. */
    data class ErrorValue(val code: String) : CellValue
}

data class Cell(
    /** Zero-based. */
    val column: Int,
    val value: CellValue,
)

data class Row(
    /** Zero-based. */
    val index: Int,
    /** Only cells that carry a value; a sparse sheet stays sparse. */
    val cells: List<Cell>,
)

data class Sheet(
    val name: String,
    val rows: List<Row>,
    val columnCount: Int,
    val rowCount: Int,
    /** True when reading stopped at a limit rather than at the end of the sheet. */
    val truncated: Boolean,
) {
    fun cellAt(row: Int, column: Int): CellValue? =
        rows.firstOrNull { it.index == row }?.cells?.firstOrNull { it.column == column }?.value
}

/** Sheet names and their part paths, read before any sheet body is parsed. */
data class SheetRef(val name: String, val path: String)

/**
 * Everything needed to read any sheet in the workbook: the sheet list, the
 * shared string table, and which style indices mean "this is a date".
 */
data class WorkbookIndex(
    val sheets: List<SheetRef>,
    val sharedStrings: List<String>,
    val dateStyles: Set<Int>,
    /** Workbooks authored on classic Mac Excel count from 1904, not 1900. */
    val epoch1904: Boolean,
)

/** Bounds on what a single read will pull into memory. */
data class ReadLimits(
    val maxRows: Int = 20_000,
    val maxColumns: Int = 512,
    val maxSharedStrings: Int = 200_000,
)
