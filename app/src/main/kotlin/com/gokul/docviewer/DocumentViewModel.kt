package com.gokul.docviewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gokul.docviewer.core.Detection
import com.gokul.docviewer.core.DocumentFormat
import com.gokul.docviewer.core.FormatDetector
import com.gokul.docviewer.data.ContentUriByteSource
import com.gokul.docviewer.data.RecentDocument
import com.gokul.docviewer.data.RecentsStore
import com.gokul.docviewer.data.canStillRead
import com.gokul.docviewer.data.readMetadata
import com.gokul.docviewer.data.tryPersistReadGrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An open document and everything the UI needs to decide what to show. */
data class OpenDocument(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val detection: Detection,
    /** What the sending app claimed the type was. Shown only in diagnostics. */
    val declaredMimeType: String?,
)

sealed interface ViewerState {
    data object Home : ViewerState
    data object Opening : ViewerState
    data class Open(val document: OpenDocument) : ViewerState

    /**
     * The file could not be read at all. Distinct from "opened but unsupported":
     * this one is usually a lapsed permission, which the user can fix by
     * picking the file again.
     */
    data class Unreadable(val displayName: String?, val reason: String, val recoverable: Boolean) :
        ViewerState
}

class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val recentsStore = RecentsStore(application)

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Home)
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    val recents: StateFlow<List<RecentDocument>> = recentsStore.recents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * @param fromPicker true when the URI came from the system document picker,
     *   which is the only source whose read grant can be made durable.
     */
    fun open(uri: Uri, fromPicker: Boolean) {
        _state.value = ViewerState.Opening
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver

            val opened = withContext(Dispatchers.IO) {
                if (!resolver.canStillRead(uri)) return@withContext null

                // Worth attempting even for intent-supplied URIs: it costs one
                // call and occasionally succeeds when the sender granted it.
                val persisted = if (fromPicker) resolver.tryPersistReadGrant(uri) else false

                val metadata = resolver.readMetadata(uri)
                val detection = FormatDetector.detect(
                    ContentUriByteSource(resolver, uri),
                    metadata.displayName,
                )
                Triple(metadata, detection, persisted)
            }

            if (opened == null) {
                _state.value = ViewerState.Unreadable(
                    displayName = null,
                    reason = "This file could not be opened. The app's permission to read it " +
                        "may have expired, which happens when a file is shared from another " +
                        "app and then reopened later.",
                    recoverable = true,
                )
                return@launch
            }

            val (metadata, detection, persisted) = opened
            val name = metadata.displayName ?: "Untitled"

            _state.value = ViewerState.Open(
                OpenDocument(
                    uri = uri,
                    displayName = name,
                    sizeBytes = metadata.sizeBytes,
                    detection = detection,
                    declaredMimeType = metadata.declaredMimeType,
                ),
            )

            recentsStore.remember(
                RecentDocument(
                    uri = uri.toString(),
                    displayName = name,
                    formatLabel = detection.format.label,
                    lastOpenedAt = System.currentTimeMillis(),
                    hasPersistedGrant = persisted,
                ),
            )
        }
    }

    fun reopen(recent: RecentDocument) = open(Uri.parse(recent.uri), fromPicker = false)

    fun forget(recent: RecentDocument) {
        viewModelScope.launch { recentsStore.forget(recent.uri) }
    }

    fun closeDocument() {
        _state.value = ViewerState.Home
    }

    /** The Drive URL behind a `.gdoc`/`.gsheet` stub, for handing off to Drive. */
    suspend fun driveUrlFor(document: OpenDocument): String? {
        if (document.detection.format != DocumentFormat.GOOGLE_SHORTCUT) return null
        val resolver = getApplication<Application>().contentResolver
        return withContext(Dispatchers.IO) {
            FormatDetector.googleShortcutUrl(ContentUriByteSource(resolver, document.uri))
        }
    }
}
