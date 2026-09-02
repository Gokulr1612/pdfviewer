package com.gokul.docviewer.core.xlsx

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class CellRefTest {

    @Test
    fun `column letters are base-26 with no zero digit`() {
        assertEquals(0, CellRef.columnToIndex("A"))
        assertEquals(25, CellRef.columnToIndex("Z"))
        // The interesting one: AA follows Z, so this is not plain base-26.
        assertEquals(26, CellRef.columnToIndex("AA"))
        assertEquals(51, CellRef.columnToIndex("AZ"))
        assertEquals(52, CellRef.columnToIndex("BA"))
        assertEquals(701, CellRef.columnToIndex("ZZ"))
        assertEquals(702, CellRef.columnToIndex("AAA"))
    }

    @Test
    fun `column indices round-trip back to letters`() {
        listOf(0, 25, 26, 51, 52, 701, 702, 16_383).forEach { index ->
            val letters = CellRef.indexToColumn(index)
            assertEquals(index, CellRef.columnToIndex(letters), "round trip failed for $letters")
        }
    }

    @Test
    fun `parses references into zero-based coordinates`() {
        assertEquals(0 to 0, CellRef.parse("A1"))
        assertEquals(1 to 11, CellRef.parse("B12"))
        assertEquals(26 to 99, CellRef.parse("AA100"))
    }

    @Test
    fun `ignores absolute reference markers`() {
        assertEquals(1 to 11, CellRef.parse("\$B\$12"))
    }

    @Test
    fun `rejects malformed references`() {
        assertNull(CellRef.parse("12"))
        assertNull(CellRef.parse("B"))
        assertNull(CellRef.parse("B0"))
        assertNull(CellRef.parse(""))
        assertNull(CellRef.parse("!1"))
        assertNull(CellRef.columnToIndex("AAAAAA"))
    }
}
