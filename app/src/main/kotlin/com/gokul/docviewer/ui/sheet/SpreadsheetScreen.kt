package com.gokul.docviewer.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gokul.docviewer.SpreadsheetState

@Composable
fun SpreadsheetScreen(
    state: SpreadsheetState,
    onSelectSheet: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is SpreadsheetState.Loading -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is SpreadsheetState.Failed -> SheetError(state.message, modifier)

        is SpreadsheetState.Loaded -> Column(modifier.fillMaxSize()) {
            // Only worth the vertical space when there is a choice to make.
            if (state.sheetNames.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = state.selectedSheet,
                    edgePadding = 8.dp,
                ) {
                    state.sheetNames.forEachIndexed { index, name ->
                        Tab(
                            selected = index == state.selectedSheet,
                            onClick = { onSelectSheet(index) },
                            text = { Text(name, maxLines = 1) },
                        )
                    }
                }
            }

            val sheet = state.sheet
            if (sheet == null || sheet.rows.isEmpty()) {
                EmptySheet(Modifier.fillMaxSize())
            } else {
                SheetGrid(sheet = sheet, epoch1904 = state.epoch1904)
            }
        }
    }
}

@Composable
private fun EmptySheet(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "This sheet is empty.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SheetError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text("Couldn't read this spreadsheet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
