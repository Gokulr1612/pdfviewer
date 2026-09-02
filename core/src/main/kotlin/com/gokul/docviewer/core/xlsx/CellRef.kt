package com.gokul.docviewer.core.xlsx

/**
 * Conversions for spreadsheet cell references like `A1`, `AA17`, `$B$4`.
 *
 * Columns are base-26 but with no zero digit — after `Z` comes `AA`, not `BA` —
 * so this is not quite a radix conversion and is worth having in one tested
 * place.
 */
object CellRef {

    /** `"A"` to 0, `"Z"` to 25, `"AA"` to 26. Null if [letters] is not a column. */
    fun columnToIndex(letters: String): Int? {
        if (letters.isEmpty()) return null
        var index = 0
        for (character in letters) {
            val value = when (character) {
                in 'A'..'Z' -> character - 'A'
                in 'a'..'z' -> character - 'a'
                else -> return null
            }
            index = index * 26 + (value + 1)
            if (index > MAX_COLUMNS) return null
        }
        return index - 1
    }

    /** 0 to `"A"`, 26 to `"AA"`. Used for column headings. */
    fun indexToColumn(index: Int): String {
        require(index >= 0) { "Column index cannot be negative: $index" }
        val letters = StringBuilder()
        var remaining = index
        while (true) {
            letters.append('A' + remaining % 26)
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return letters.reverse().toString()
    }

    /** Splits `"B12"` into a zero-based column and row. Null if malformed. */
    fun parse(reference: String): Pair<Int, Int>? {
        val cleaned = reference.replace("$", "")
        val split = cleaned.indexOfFirst { it.isDigit() }
        if (split <= 0) return null
        val column = columnToIndex(cleaned.substring(0, split)) ?: return null
        val row = cleaned.substring(split).toIntOrNull() ?: return null
        if (row < 1) return null
        return column to (row - 1)
    }

    /** Excel's own hard limit, and a useful guard against malformed input. */
    const val MAX_COLUMNS = 16_384
}
