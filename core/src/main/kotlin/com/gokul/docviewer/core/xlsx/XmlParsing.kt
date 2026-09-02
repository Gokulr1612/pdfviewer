package com.gokul.docviewer.core.xlsx

import org.xmlpull.v1.XmlPullParser

/**
 * Supplies pull parsers without binding this module to one implementation.
 *
 * Android has a fast Expat-backed parser reachable through `android.util.Xml`;
 * a plain JVM needs kxml2 or similar. Injecting the factory is what lets the
 * OOXML reader be exercised by ordinary unit tests while still using the
 * platform's own parser on a device.
 */
fun interface XmlParserFactory {
    fun newParser(): XmlPullParser
}

/**
 * Walks the direct children of the current element, invoking [onElement] at the
 * START_TAG of each and guaranteeing the parser is positioned past that
 * element's END_TAG afterwards.
 *
 * Hand-rolled descent through OOXML is easy to get subtly wrong — one forgotten
 * skip and the parser is reading a sibling's content. Centralising the walk
 * here means each handler only has to decide whether it cares about a tag.
 */
internal inline fun XmlPullParser.forEachChild(onElement: (name: String) -> Unit) {
    val depth = this.depth
    while (true) {
        when (next()) {
            XmlPullParser.START_TAG -> {
                if (this.depth != depth + 1) continue
                val name = this.name
                onElement(name)
                // The handler may or may not have consumed the element; skip
                // whatever is left of it either way.
                while (!(eventType == XmlPullParser.END_TAG && this.name == name && this.depth == depth + 1)) {
                    if (eventType == XmlPullParser.END_DOCUMENT) return
                    next()
                }
            }
            XmlPullParser.END_TAG -> if (this.depth == depth) return
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}

/** Concatenated text of the current element, including nested runs. */
internal fun XmlPullParser.readElementText(): String {
    val depth = this.depth
    val text = StringBuilder()
    while (true) {
        when (next()) {
            XmlPullParser.TEXT -> text.append(this.text)
            XmlPullParser.END_TAG -> if (this.depth == depth) return text.toString()
            XmlPullParser.END_DOCUMENT -> return text.toString()
        }
    }
}

internal fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)
