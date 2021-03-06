package org.tokend.template.view.util.formatter

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class DateFormatter(private val context: Context) {
    /**
     * Formats given date to the long string:
     * full month name, full year number, 12-/24-hour time based on device preference
     */
    fun formatLong(date: Date): String {
        return "${formatDateOnly(date)} ${formatTimeOnly(date)}"
    }

    /**
     * Formats given date to the compact string:
     * short month name, 2-digits year number, 12-/24-hour time based on device preference
     */
    fun formatCompact(date: Date): String {
        val dateFormat = SimpleDateFormat("dd MMM yy", Locale.ENGLISH)
        val formattedDate = dateFormat.format(date)

        return "$formattedDate ${formatTimeOnly(date)}"
    }

    /**
     * Formats given date to the long string without time:
     * full month name, full year number
     */
    fun formatDateOnly(date: Date): String {
        return SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
                .format(date)
    }

    /**
     * Formats given date to the long string with time only:
     * 12-/24-hour time based on device preference
     */
    fun formatTimeOnly(date: Date): String {
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        return timeFormat.format(date)
    }
}