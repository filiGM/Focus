package com.focuslock.app

import android.content.Context
import java.util.Calendar
import java.util.Locale

/** The "blocked hours" rule: all tracked apps are blocked between a start and an end time, every day. */
object Schedule {

    fun fmt(minuteOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

    /** True when the time-range rule is switched on and has a usable range. */
    fun enabled(c: Context): Boolean =
        Prefs.modeSchedule(c) && Prefs.schedStart(c) != Prefs.schedEnd(c)

    private fun minuteOfDay(now: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun inRange(start: Int, end: Int, m: Int): Boolean =
        if (start < end) (m >= start && m < end) else (m >= start || m < end)

    /** Length of the blocked range in minutes (handles ranges that cross midnight). */
    fun lengthMinutes(start: Int, end: Int): Int =
        if (end > start) end - start else 24 * 60 - start + end

    /** When the blocked period that contains [now] ends, or 0 if [now] is outside the range. */
    fun endOfActive(start: Int, end: Int, now: Long): Long {
        if (start == end) return 0L
        val m = minuteOfDay(now)
        if (!inRange(start, end, m)) return 0L
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, end / 60)
            set(Calendar.MINUTE, end % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (start > end && m >= start) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** End of the current blocked period according to the saved settings, or 0 if not blocked now. */
    fun activeUntil(c: Context, now: Long = System.currentTimeMillis()): Long {
        if (!enabled(c)) return 0L
        return endOfActive(Prefs.schedStart(c), Prefs.schedEnd(c), now)
    }

    fun isActive(c: Context, now: Long = System.currentTimeMillis()): Boolean = activeUntil(c, now) > 0L
}
