package com.ken1dot.spartanlock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class UpcomingCalendarEvent(
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String = ""
) {
    fun timeLabel(is24: Boolean = false): String {
        if (allDay) {
            val day = SimpleDateFormat("EEE d MMM", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return "All day · ${day.format(Date(beginMillis))}"
        }
        val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())
        val day = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val start = Date(beginMillis)
        val end = Date(endMillis)
        val today = day.format(Date())
        val prefix = if (day.format(start) == today) "" else "${day.format(start)} · "
        return "$prefix${time.format(start)}—${time.format(end)}"
    }
}

object CalendarEvents {
    fun next(context: Context, nowMillis: Long = System.currentTimeMillis()): UpcomingCalendarEvent? {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED) return null

        val horizon = nowMillis + 7L * 24 * 60 * 60 * 1000
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
        )
        return runCatching {
            var next: UpcomingCalendarEvent? = null
            CalendarContract.Instances.query(
                context.contentResolver, projection, nowMillis, horizon
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0)?.trim().orEmpty()
                    val begin = cursor.getLong(1)
                    val end = cursor.getLong(2)
                    if (title.isNotEmpty() && end > nowMillis &&
                        begin < (next?.beginMillis ?: Long.MAX_VALUE)) {
                        next = UpcomingCalendarEvent(title, begin, end, cursor.getInt(3) != 0, cursor.getString(4)?.trim().orEmpty())
                    }
                }
            }
            next
        }.getOrNull()
    }
}
