package com.gokul.docviewer.core.xlsx

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ExcelDatesTest {

    @Test
    fun `converts serials in the 1900 system`() {
        assertEquals(LocalDateTime.of(2024, 1, 31, 0, 0), ExcelDates.toDateTime(45322.0, false))
        assertEquals(LocalDateTime.of(2000, 1, 1, 0, 0), ExcelDates.toDateTime(36526.0, false))
    }

    @Test
    fun `absorbs Excel's fictional 29 February 1900`() {
        // Excel keeps Lotus 1-2-3's leap year bug, so serial 61 is 1 March 1900
        // and every later date is offset by a day from a naive count.
        assertEquals(LocalDateTime.of(1900, 3, 1, 0, 0), ExcelDates.toDateTime(61.0, false))
    }

    @Test
    fun `converts serials in the 1904 system`() {
        assertEquals(LocalDateTime.of(1904, 1, 1, 0, 0), ExcelDates.toDateTime(0.0, true))
        assertEquals(LocalDateTime.of(1905, 1, 1, 0, 0), ExcelDates.toDateTime(366.0, true))
    }

    @Test
    fun `reads the fractional part as a time of day`() {
        assertEquals(LocalDateTime.of(2024, 1, 31, 12, 0), ExcelDates.toDateTime(45322.5, false))
        assertEquals(LocalDateTime.of(2024, 1, 31, 6, 0), ExcelDates.toDateTime(45322.25, false))
    }

    @Test
    fun `formats by what the serial actually carries`() {
        assertEquals("2024-01-31", ExcelDates.format(45322.0, false))
        assertEquals("2024-01-31 12:00", ExcelDates.format(45322.5, false))
        // Below 1 there is no date part at all, only a time.
        assertEquals("06:00:00", ExcelDates.format(0.25, false))
    }

    @Test
    fun `identifies built-in date formats`() {
        assertTrue(ExcelDates.isDateFormat(14, null))
        assertTrue(ExcelDates.isDateFormat(22, null))
        assertTrue(ExcelDates.isDateFormat(45, null))
        assertFalse(ExcelDates.isDateFormat(0, null))
        assertFalse(ExcelDates.isDateFormat(4, null))
    }

    @Test
    fun `identifies custom date formats without being fooled by literals`() {
        assertTrue(ExcelDates.isDateFormat(164, "dd/mm/yyyy"))
        assertTrue(ExcelDates.isDateFormat(165, "d mmm yyyy"))
        assertTrue(ExcelDates.isDateFormat(166, "hh:mm:ss"))
        // A quoted "m" is a literal, and a bracketed section is a colour or
        // condition — neither makes this a date.
        assertFalse(ExcelDates.isDateFormat(167, "\"m\"#,##0"))
        assertFalse(ExcelDates.isDateFormat(168, "[Red]#,##0.00"))
        assertFalse(ExcelDates.isDateFormat(169, "0.00%"))
    }

    @Test
    fun `renders whole numbers without a trailing decimal`() {
        assertEquals("42", ExcelDates.formatNumber(42.0))
        assertEquals("-7", ExcelDates.formatNumber(-7.0))
        assertEquals("42.5", ExcelDates.formatNumber(42.5))
    }
}
