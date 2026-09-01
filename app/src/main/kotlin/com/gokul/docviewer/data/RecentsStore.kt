package com.gokul.docviewer.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentsDataStore by preferencesDataStore(name = "recents")

data class RecentDocument(
    val uri: String,
    val displayName: String,
    val formatLabel: String,
    val lastOpenedAt: Long,
    /**
     * Whether a long-lived read grant was obtained.
     *
     * This is the difference between a document that will reliably reopen and
     * one that probably will not. Only URIs from the system picker can be
     * persisted; a `content://` URI arriving from a share or an "open with"
     * intent carries a grant that dies with the task, and Android offers no way
     * to extend it. Recording which is which lets the UI recover gracefully
     * instead of showing an error the user cannot act on.
     */
    val hasPersistedGrant: Boolean,
)

class RecentsStore(private val context: Context) {

    private val key = stringPreferencesKey("entries")

    val recents: Flow<List<RecentDocument>> =
        context.recentsDataStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun remember(document: RecentDocument) {
        context.recentsDataStore.edit { prefs ->
            val existing = decode(prefs[key]).filterNot { it.uri == document.uri }
            prefs[key] = encode((listOf(document) + existing).take(MAX_ENTRIES))
        }
    }

    suspend fun forget(uri: String) {
        context.recentsDataStore.edit { prefs ->
            prefs[key] = encode(decode(prefs[key]).filterNot { it.uri == uri })
        }
    }

    suspend fun clear() {
        context.recentsDataStore.edit { it.remove(key) }
    }

    private fun encode(entries: List<RecentDocument>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("uri", entry.uri)
                    put("name", entry.displayName)
                    put("format", entry.formatLabel)
                    put("at", entry.lastOpenedAt)
                    put("persisted", entry.hasPersistedGrant)
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<RecentDocument> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val uri = item.optString("uri")
                if (uri.isBlank()) return@mapNotNull null
                RecentDocument(
                    uri = uri,
                    displayName = item.optString("name", "Untitled"),
                    formatLabel = item.optString("format", ""),
                    lastOpenedAt = item.optLong("at"),
                    hasPersistedGrant = item.optBoolean("persisted", false),
                )
            }
        } catch (e: org.json.JSONException) {
            // Corrupt store: better an empty recents list than a crash on launch.
            emptyList()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 40
    }
}

/**
 * Asks for a durable read grant, reporting whether one was actually obtained.
 *
 * Succeeds for documents chosen through the system picker. Fails, by design,
 * for URIs handed over by another app — so the caller must treat a `false`
 * result as normal rather than exceptional.
 */
fun ContentResolver.tryPersistReadGrant(uri: Uri): Boolean =
    try {
        takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        false
    }
