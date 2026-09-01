package com.gokul.docviewer.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.gokul.docviewer.core.ByteSource
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Bridges Android's content resolver to the Android-free [ByteSource] the
 * detector and parsers work against.
 *
 * Deliberately re-opens the stream on each call rather than buffering: a
 * document may be tens of megabytes and the format layer only ever needs a
 * bounded prefix.
 */
class ContentUriByteSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : ByteSource {
    override fun open(): InputStream =
        resolver.openInputStream(uri)
            ?: throw FileNotFoundException("The content provider returned no stream for $uri")
}

/** What the system can tell us about a document without opening it. */
data class DocumentMetadata(
    val displayName: String?,
    val sizeBytes: Long?,
    /** The type the *sending app* claimed. Recorded for diagnostics, never trusted. */
    val declaredMimeType: String?,
)

fun ContentResolver.readMetadata(uri: Uri): DocumentMetadata {
    var name: String? = null
    var size: Long? = null

    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        try {
            query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            name = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
        } catch (e: SecurityException) {
            // The grant has already lapsed; fall through to the URI's own path.
        } catch (e: IllegalArgumentException) {
            // Some providers reject columns they do not implement.
        }
    }

    if (name == null) {
        name = uri.lastPathSegment?.substringAfterLast('/')
    }

    return DocumentMetadata(
        displayName = name,
        sizeBytes = size,
        declaredMimeType = runCatching { getType(uri) }.getOrNull(),
    )
}

/** True when the document can still be read. Cheap: opens and closes a stream. */
fun ContentResolver.canStillRead(uri: Uri): Boolean = try {
    openInputStream(uri)?.use { true } ?: false
} catch (e: SecurityException) {
    false
} catch (e: FileNotFoundException) {
    false
} catch (e: IOException) {
    false
}
