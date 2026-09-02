package com.gokul.docviewer.core.xlsx

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Excel stores dates as a day count, and getting them back out has two
 * historical traps.
 *
 * The first is that the 1900 system deliberately contains a bug: Excel treats
 * 1900 as a leap year for compatibility with Lotus 1-2-3, so serial 60 is a
 * 29 February that never existed and everything from serial 61 onwards is
 * offset by one day. Anchoring at 1899-12-30 absorbs that for every date after
 * February 1900, which is every date that occurs in practice.
 *
 * The second is that workbooks written by classic Mac Excel count from 1904
 * instead, which is a per-workbook flag rather than anything visible in the
 * cell.
 */
object ExcelDates {

    /** Days from the 1900-system serial 0 to the Unix-friendly anchor. */
    private val EPOCH_1900: LocalDateTime = LocalDateTime.of(1899, 12, 30, 0, 0)
    private val EPOCH_1904: LocalDateTime = LocalDateTime.of(1904, 1, 1, 0, 0)

    private const val SECONDS_PER_DAY = 86_400.0

    fun toDateTime(serial: Double, epoch1904: Boolean): LocalDateTime {
        val epoch = if (epoch1904) EPOCH_1904 else EPOCH_1900
        val wholeDays = serial.toLong()
        val fractionOfDay = serial - wholeDays
        // Rounding to the second avoids 10:29:59.9999 from binary fractions.
        val seconds = (fractionOfDay * SECONDS_PER_DAY).roundToLong()
        return epoch.plusDays(wholeDays).plusSeconds(seconds)
    }

    private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * Renders a date cell. A serial below 1 carries no date part, so it is a
     * time of day; a whole number carries no time part.
     */
    fun format(serial: Double, epoch1904: Boolean): String {
        val moment = toDateTime(serial, epoch1904)
        val hasDate = abs(serial) >= 1.0
        val hasTime = serial % 1.0 != 0.0
        return when {
            !hasDate && hasTime -> moment.format(TIME_ONLY)
            hasTime -> moment.format(DATE_TIME)
            else -> moment.format(DATE_ONLY)
        }
    }

    /**
     * Built-in number format ids that mean a date or a time. Excel reserves
     * 0–163 and does not write these out, so they have to be known rather than
     * read from the file.
     */
    private val BUILT_IN_DATE_FORMATS: Set<Int> =
        (14..22).toSet() + (45..47).toSet() + setOf(27, 30, 36, 50, 57)

    /**
     * Whether a format code describes a date. Quoted literals and colour or
     * condition sections are stripped first, so a currency format like
     * `"m"#,##0` is not mistaken for a month.
     */
    fun isDateFormat(numFmtId: Int, formatCode: String?): Boolean {
        if (numFmtId in BUILT_IN_DATE_FORMATS) return true
        if (formatCode == null) return false

        val stripped = formatCode
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\[[^]]*]"), "")
            .replace(Regex("\\\\."), "")

        return stripped.any { it in "yYdDhHsm" } &&
            // A bare "General" or a pure number mask is not a date.
            stripped.any { it in "yYdDhH" || it == 's' }
    }

    /** Trims the trailing `.0` that every whole number would otherwise carry. */
    fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble() && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
}
