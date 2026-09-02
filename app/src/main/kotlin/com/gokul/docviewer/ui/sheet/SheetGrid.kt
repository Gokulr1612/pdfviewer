package com.gokul.docviewer.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gokul.docviewer.core.xlsx.CellRef
import com.gokul.docviewer.core.xlsx.CellValue
import com.gokul.docviewer.core.xlsx.Sheet
import com.gokul.docviewer.core.xlsx.displayText

private val ColumnWidth = 104.dp
private val RowHeaderWidth = 52.dp
private val RowHeight = 36.dp

/**
 * Renders one sheet as a scrollable grid.
 *
 * The row header and the column header both stay put while the body scrolls,
 * which is what makes a wide sheet readable on a phone: without it you lose
 * track of which column you are looking at within about three swipes.
 *
 * Both axes share one horizontal scroll state so the header cannot drift out
 * of alignment with the cells beneath it.
 */
@Composable
fun SheetGrid(
    sheet: Sheet,
    epoch1904: Boolean,
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()

    // A sparse sheet stores only populated rows, but the grid has to render
    // the blank ones in between, so index by row number for O(1) lookup.
    val rowsByIndex = remember(sheet) { sheet.rows.associateBy { it.index } }

    Column(modifier.fillMaxSize()) {
        ColumnHeader(sheet.columnCount, horizontalScroll)

        LazyColumn(Modifier.fillMaxSize()) {
            items(sheet.rowCount) { rowIndex ->
                val cells = remember(sheet, rowIndex) {
                    rowsByIndex[rowIndex]?.cells?.associateBy { it.column }.orEmpty()
                }
                Row(Modifier.height(RowHeight)) {
                    RowHeaderCell(rowIndex + 1)
                    Row(Modifier.horizontalScroll(horizontalScroll)) {
                        repeat(sheet.columnCount) { column ->
                            BodyCell(cells[column]?.value, epoch1904)
                        }
                    }
                }
            }

            if (sheet.truncated) {
                item { TruncationNotice(sheet) }
            }
        }
    }
}

@Composable
private fun ColumnHeader(
    columnCount: Int,
    horizontalScroll: androidx.compose.foundation.ScrollState,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // The empty corner above the row numbers.
        Box(Modifier.width(RowHeaderWidth).fillMaxSize())
        Row(Modifier.horizontalScroll(horizontalScroll)) {
            repeat(columnCount) { column ->
                HeaderText(CellRef.indexToColumn(column), ColumnWidth)
            }
        }
    }
}

@Composable
private fun RowHeaderCell(number: Int) {
    Box(
        Modifier
            .width(RowHeaderWidth)
            .height(RowHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeaderText(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(width).height(RowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BodyCell(value: CellValue?, epoch1904: Boolean) {
    Box(
        Modifier.width(ColumnWidth).height(RowHeight).padding(horizontal = 8.dp),
        // Numbers read far better right-aligned, and it is the quickest visual
        // cue that a cell holds a number rather than text that looks like one.
        contentAlignment = if (value.isNumeric()) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (value != null) {
            Text(
                text = value.displayText(epoch1904),
                style = MaterialTheme.typography.bodySmall,
                color = when (value) {
                    is CellValue.ErrorValue -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (value.isNumeric()) TextAlign.End else TextAlign.Start,
            )
        }
    }
}

private fun CellValue?.isNumeric(): Boolean =
    this is CellValue.Number || this is CellValue.DateTime

@Composable
private fun TruncationNotice(sheet: Sheet) {
    Text(
        text = "Showing the first ${sheet.rows.size} rows of this sheet. " +
            "The rest were left out to keep the file open quickly.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}
