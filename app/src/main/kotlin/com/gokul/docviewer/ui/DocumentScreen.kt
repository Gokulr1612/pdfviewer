package com.gokul.docviewer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.gokul.docviewer.OpenDocument
import com.gokul.docviewer.SpreadsheetState
import com.gokul.docviewer.core.DocumentFormat
import com.gokul.docviewer.ui.sheet.SpreadsheetScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    document: OpenDocument,
    spreadsheet: SpreadsheetState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSelectSheet: (Int) -> Unit,
    onOpenDriveShortcut: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = document.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share this document")
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        val format = document.detection.format

        when {
            format == DocumentFormat.PDF -> PdfViewerHost(document.uri, content)

            format.isSpreadsheet -> SpreadsheetScreen(
                state = spreadsheet,
                onSelectSheet = onSelectSheet,
                modifier = content,
            )

            // Recognised, renderer still to come (phase 3).
            format.isWordProcessing -> RendererNotBuiltYet(format, content)

            else -> UnsupportedDocument(
                detection = document.detection,
                modifier = content,
                onAction = if (format == DocumentFormat.GOOGLE_SHORTCUT) onOpenDriveShortcut else null,
            )
        }
    }
}

@Composable
fun OpeningDocument(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
