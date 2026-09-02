package com.gokul.docviewer.core

import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * A re-openable stream of bytes.
 *
 * Detection needs to read a file more than once — the header, then possibly the
 * ZIP central directory — and Android's `content://` streams are not reliably
 * markable or seekable. Rather than buffering whole documents in memory, the
 * caller supplies something that can hand out a fresh stream on demand.
 */
fun interface ByteSource {
    /** Opens a new stream positioned at the first byte. Caller closes it. */
    fun open(): InputStream

    companion object {
        fun of(bytes: ByteArray): ByteSource = ByteSource { ByteArrayInputStream(bytes) }
    }
}

/**
 * Reads up to [max] bytes from the start. Returns fewer if the source is
 * shorter; never throws on a short file.
 */
internal fun ByteSource.readHeader(max: Int): ByteArray =
    open().use { stream ->
        val buffer = ByteArray(max)
        var filled = 0
        while (filled < max) {
            val read = stream.read(buffer, filled, max - filled)
            if (read <= 0) break
            filled += read
        }
        if (filled == max) buffer else buffer.copyOf(filled)
    }

/** True if [this] begins with [prefix]. */
internal fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) if (this[i] != prefix[i]) return false
    return true
}

/** Index of [needle] within the first [limit] bytes, or -1. */
internal fun ByteArray.indexOf(needle: ByteArray, limit: Int = size): Int {
    if (needle.isEmpty()) return 0
    val end = minOf(size, limit) - needle.size
    outer@ for (start in 0..end) {
        for (i in needle.indices) {
            if (this[start + i] != needle[i]) continue@outer
        }
        return start
    }
    return -1
}
