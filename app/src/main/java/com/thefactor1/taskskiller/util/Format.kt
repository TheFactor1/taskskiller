package com.thefactor1.taskskiller.util

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.Date

object Format {

    /** "45 min", "2 h", "1 h 30 min". */
    fun interval(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$minutes min"
            rest == 0 -> "$hours h"
            else -> "$hours h $rest min"
        }
    }

    /** Clock time for today, date + time for anything further out. */
    fun timestamp(context: Context, millis: Long): String {
        if (millis <= 0L) return "—"
        val date = Date(millis)
        return if (DateUtils.isToday(millis)) {
            DateFormat.getTimeFormat(context).format(date)
        } else {
            "${DateFormat.getDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
        }
    }

    fun relative(millis: Long): String =
        if (millis <= 0L) "—"
        else DateUtils.getRelativeTimeSpanString(
            millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString()
}
