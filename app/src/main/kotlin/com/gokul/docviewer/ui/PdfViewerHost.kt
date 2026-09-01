package com.gokul.docviewer.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.pdf.viewer.fragment.PdfViewerFragment

/**
 * The app's single point of contact with androidx.pdf.
 *
 * That library is at 1.0.0-beta01 and its API is still moving, so every use of
 * it is funnelled through this one composable. If the surface changes, or if we
 * ever need to fall back to the platform `PdfRenderer` for a device the
 * backported renderer does not reach, this is the only file that changes.
 *
 * Hosting a fragment means the activity must be a `FragmentActivity`; see
 * [com.gokul.docviewer.MainActivity].
 */
@Composable
fun PdfViewerHost(uri: Uri, modifier: Modifier = Modifier) {
    AndroidFragment<PdfViewerFragment>(modifier = modifier) { fragment ->
        // Assigning the same URI again would restart loading and throw away the
        // user's scroll position on every recomposition.
        if (fragment.documentUri != uri) {
            fragment.documentUri = uri
        }
    }
}
