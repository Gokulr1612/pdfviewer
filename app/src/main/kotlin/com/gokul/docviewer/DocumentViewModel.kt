package com.gokul.docviewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gokul.docviewer.core.ByteSource
import com.gokul.docviewer.core.Detection
import com.gokul.docviewer.core.DocumentFormat
import com.gokul.docviewer.core.FormatDetector
import com.gokul.docviewer.data.ContentUriByteSource
import com.gokul.docviewer.data.RecentDocument
import com.gokul.docviewer.data.RecentsStore
import com.gokul.docviewer.data.canStillRead
import com.gokul.docviewer.data.readMetadata
import com.gokul.docviewer.data.tryPersistReadGrant
import com.gokul.docviewer.core.xlsx.Sheet
import com.gokul.docviewer.core.xlsx.WorkbookIndex
import com.gokul.docviewer.core.xlsx.XlsxFormatException
import com.gokul.docviewer.core.xlsx.XlsxReader
import com.gokul.docviewer.core.xlsx.XmlParserFactory
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

/** The spreadsheet viewer's own state, nested inside an open document. */
sealed interface SpreadsheetState {
    data object Loading : SpreadsheetState
    data class Failed(val message: String) : SpreadsheetState
    data class Loaded(
        val sheetNames: List<String>,
        val selectedSheet: Int,
        val sheet: Sheet?,
        val epoch1904: Boolean,
    ) : SpreadsheetState
}

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

    // android.util.Xml gives the platform's Expat-backed parser; the core
    // module stays free of Android types by taking it as a factory.
    private val xlsxReader = XlsxReader(XmlParserFactory { android.util.Xml.newPullParser() })

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Home)
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private val _spreadsheet = MutableStateFlow<SpreadsheetState>(SpreadsheetState.Loading)
    val spreadsheet: StateFlow<SpreadsheetState> = _spreadsheet.asStateFlow()

    /** Kept so switching sheets does not re-parse the workbook's shared parts. */
    private var workbookIndex: WorkbookIndex? = null
    private var workbookSource: ByteSource? = null

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

            if (detection.format.isSpreadsheet) {
                loadWorkbook(ContentUriByteSource(resolver, uri))
            }

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
        _spreadsheet.value = SpreadsheetState.Loading
        workbookIndex = null
        workbookSource = null
    }

    private suspend fun loadWorkbook(source: ByteSource) {
        _spreadsheet.value = SpreadsheetState.Loading
        workbookSource = source
        workbookIndex = null

        val loaded = withContext(Dispatchers.IO) {
            try {
                val index = xlsxReader.readIndex(source)
                val sheet = if (index.sheets.isEmpty()) {
                    null
                } else {
                    xlsxReader.readSheet(source, index, 0)
                }
                Result.success(index to sheet)
            } catch (e: XlsxFormatException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        loaded.fold(
            onSuccess = { (index, sheet) ->
                workbookIndex = index
                _spreadsheet.value = SpreadsheetState.Loaded(
                    sheetNames = index.sheets.map { it.name },
                    selectedSheet = 0,
                    sheet = sheet,
                    epoch1904 = index.epoch1904,
                )
            },
            onFailure = { error ->
                _spreadsheet.value = SpreadsheetState.Failed(
                    error.message ?: "The file could not be read as a spreadsheet.",
                )
            },
        )
    }

    fun selectSheet(sheetIndex: Int) {
        val index = workbookIndex ?: return
        val source = workbookSource ?: return
        val current = _spreadsheet.value as? SpreadsheetState.Loaded ?: return
        if (sheetIndex == current.selectedSheet) return

        // Show the tab as selected straight away; the body follows.
        _spreadsheet.value = current.copy(selectedSheet = sheetIndex, sheet = null)
        viewModelScope.launch {
            val sheet = withContext(Dispatchers.IO) {
                runCatching { xlsxReader.readSheet(source, index, sheetIndex) }.getOrNull()
            }
            val latest = _spreadsheet.value as? SpreadsheetState.Loaded ?: return@launch
            if (latest.selectedSheet == sheetIndex) {
                _spreadsheet.value = latest.copy(sheet = sheet)
            }
        }
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
