package com.gokul.docviewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gokul.docviewer.ui.DocViewerTheme
import com.gokul.docviewer.ui.DocumentScreen
import com.gokul.docviewer.ui.HomeScreen
import com.gokul.docviewer.ui.OpeningDocument
import com.gokul.docviewer.ui.UnreadableDocument
import kotlinx.coroutines.launch

/**
 * A [FragmentActivity] rather than a plain ComponentActivity: androidx.pdf
 * ships its viewer as a fragment, so a fragment host is required even though
 * everything else here is Compose.
 */
class MainActivity : FragmentActivity() {

    /** Set when launched from another app; consumed once the UI is composed. */
    private var pendingUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        pendingUri = intent?.let(::uriFrom)

        setContent {
            DocViewerTheme {
                DocViewerApp(
                    initialUri = pendingUri,
                    onInitialUriHandled = { pendingUri = null },
                )
            }
        }
    }

    /**
     * The activity is `singleTask`, so a second "open with" while it is already
     * running arrives here rather than through [onCreate].
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        uriFrom(intent)?.let { uri ->
            openRequests.tryEmit(uri)
        }
    }

    private fun uriFrom(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentUris.getParcelableExtra(intent, Intent.EXTRA_STREAM)
        else -> null
    }

    companion object {
        /** Bridges [onNewIntent] into the composition. */
        val openRequests =
            kotlinx.coroutines.flow.MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    }
}

/** `Intent.getParcelableExtra` is deprecated on API 33+ without a class argument. */
private object IntentUris {
    fun getParcelableExtra(intent: Intent, name: String): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(name)
        }
}

@Composable
private fun DocViewerApp(
    initialUri: Uri?,
    onInitialUriHandled: () -> Unit,
) {
    val viewModel: DocumentViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val spreadsheet by viewModel.spreadsheet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.open(it, fromPicker = true) } }

    // Types offered by the system picker. octet-stream is included because
    // plenty of correctly-formed documents are stored with no better type.
    val pickableTypes = remember {
        arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-word.document.macroEnabled.12",
            "application/vnd.ms-excel.sheet.macroEnabled.12",
            "application/octet-stream",
        )
    }

    LaunchedEffect(initialUri) {
        initialUri?.let {
            viewModel.open(it, fromPicker = false)
            onInitialUriHandled()
        }
    }

    LaunchedEffect(Unit) {
        MainActivity.openRequests.collect { uri -> viewModel.open(uri, fromPicker = false) }
    }

    when (val current = state) {
        is ViewerState.Home -> HomeScreen(
            recents = recents,
            onPickDocument = { picker.launch(pickableTypes) },
            onOpenRecent = viewModel::reopen,
            onForgetRecent = viewModel::forget,
        )

        is ViewerState.Opening -> OpeningDocument()

        is ViewerState.Open -> DocumentScreen(
            document = current.document,
            spreadsheet = spreadsheet,
            onBack = viewModel::closeDocument,
            onShare = { shareDocument(context, current.document) },
            onSelectSheet = viewModel::selectSheet,
            onOpenDriveShortcut = {
                scope.launch {
                    val url = viewModel.driveUrlFor(current.document)
                    if (url == null) {
                        Toast.makeText(
                            context,
                            "This shortcut doesn't contain a link we can follow.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        openExternally(context, Uri.parse(url))
                    }
                }
            },
        )

        is ViewerState.Unreadable -> UnreadableDocument(
            reason = current.reason,
            recoverable = current.recoverable,
            onPickAgain = { picker.launch(pickableTypes) },
            onBack = viewModel::closeDocument,
        )
    }
}

private fun shareDocument(context: android.content.Context, document: OpenDocument) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = document.detection.format.mimeType
        putExtra(Intent.EXTRA_STREAM, document.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startExternally(context, Intent.createChooser(intent, "Share ${document.displayName}"))
}

private fun openExternally(context: android.content.Context, uri: Uri) {
    startExternally(context, Intent(Intent.ACTION_VIEW, uri))
}

private fun startExternally(context: android.content.Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this device can handle that.", Toast.LENGTH_SHORT).show()
    }
}
