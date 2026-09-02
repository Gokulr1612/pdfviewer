package com.gokul.docviewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.ui.graphics.vector.ImageVector
import com.gokul.docviewer.core.Detection
import com.gokul.docviewer.core.DocumentFormat

/**
 * What to say when we recognise a file but will not render it.
 *
 * Being specific here is the whole reason the detector bothers to identify
 * formats it cannot open. "Word 97 documents aren't supported" tells someone
 * what to do next; "couldn't open file" does not.
 */
private data class Explanation(
    val icon: ImageVector,
    val headline: String,
    val detail: String,
    val actionLabel: String? = null,
)

private fun explain(detection: Detection): Explanation = when (detection.format) {
    DocumentFormat.GOOGLE_SHORTCUT -> Explanation(
        icon = Icons.Outlined.CloudOff,
        headline = "This is a shortcut, not a document",
        detail = "Google Docs and Sheets files stored on your device hold no content — " +
            "they are just a link to Google Drive. Open it in Drive, or export it as " +
            ".docx or .xlsx from Drive and open that copy here.",
        actionLabel = "Open in Drive",
    )

    DocumentFormat.ENCRYPTED_OFFICE -> Explanation(
        icon = Icons.Outlined.Lock,
        headline = "This document is password-protected",
        detail = "Opening encrypted Office files isn't supported yet. Remove the password " +
            "in Word or Excel and save a copy to read it here.",
    )

    DocumentFormat.LEGACY_DOC, DocumentFormat.LEGACY_XLS,
    DocumentFormat.LEGACY_PPT, DocumentFormat.LEGACY_OFFICE,
    -> Explanation(
        icon = Icons.Outlined.Description,
        headline = "${detection.format.label}s aren't supported",
        detail = "This is a pre-2007 Office file, which uses an entirely different format " +
            "from modern .docx and .xlsx files. Open it in Word or Excel and save it in " +
            "the newer format to read it here.",
    )

    DocumentFormat.PPTX -> Explanation(
        icon = Icons.Outlined.Description,
        headline = "Presentations aren't supported yet",
        detail = "PowerPoint files are recognised but not yet rendered. Word documents, " +
            "spreadsheets and PDFs all work today.",
    )

    DocumentFormat.ODT, DocumentFormat.ODS -> Explanation(
        icon = Icons.Outlined.Description,
        headline = "OpenDocument files aren't supported",
        detail = "Save this as .docx or .xlsx — LibreOffice and Google Drive can both do " +
            "that — and it will open here.",
    )

    DocumentFormat.RTF, DocumentFormat.CSV, DocumentFormat.ZIP -> Explanation(
        icon = Icons.Outlined.Description,
        headline = "${detection.format.label}s aren't supported",
        detail = "This app opens PDF, Word and Excel documents.",
    )

    else -> Explanation(
        icon = Icons.Outlined.Description,
        headline = "This file isn't a document we can read",
        detail = "It doesn't look like a PDF, Word or Excel file. " +
            "Detected as: ${detection.reason}.",
    )
}

@Composable
fun UnsupportedDocument(
    detection: Detection,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    val explanation = explain(detection)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = explanation.icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = explanation.headline,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = explanation.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (explanation.actionLabel != null && onAction != null) {
            Button(onClick = onAction) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, Modifier.size(18.dp))
                Text(explanation.actionLabel, Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Placeholder for formats whose renderers land in later phases. */
@Composable
fun RendererNotBuiltYet(format: DocumentFormat, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${format.label} recognised",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "The viewer for this format is still being built. The file was read and " +
                "identified correctly — there is just nothing to draw it with yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
