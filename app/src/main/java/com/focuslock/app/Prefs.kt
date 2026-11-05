package com.focuslock.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** All settings and lock state live here (SharedPreferences). */
object Prefs {

    // TikTok (+Lite), Facebook (+Lite), Instagram (+Lite), YouTube
    val DEFAULT_APPS: Set<String> = setOf(
        "com.zhiliaoapp.musically",
        "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.trill",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.instagram.lite",
        "com.google.android.youtube"
    )

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("focuslock", Context.MODE_PRIVATE)

    fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    // ---------------------------------------------------------------- on / off
    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    // ---------------------------------------------------------------- restriction type
    /** Rule 1: daily limit per app. */
    fun modeDaily(c: Context) = sp(c).getBoolean("mode_daily", true)

    /** Rule 2: blocked hours (time range). Both can be on at the same time. */
    fun modeSchedule(c: Context) = sp(c).getBoolean("mode_sched", false)

    /** Blocked-hours start / end, minutes since midnight. */
    fun schedStart(c: Context) = sp(c).getInt("sched_start", 22 * 60)
    fun schedEnd(c: Context) = sp(c).getInt("sched_end", 6 * 60)

    fun setModes(c: Context, daily: Boolean, sched: Boolean, start: Int, end: Int) {
        sp(c).edit()
            .putBoolean("mode_daily", daily)
            .putBoolean("mode_sched", sched)
            .putInt("sched_start", start)
            .putInt("sched_end", end)
            .apply()
    }

    fun rulesSummary(c: Context): String {
        val parts = ArrayList<String>()
        if (modeDaily(c)) parts.add("Daily limit " + limitMin(c) + " min per app")
        if (modeSchedule(c)) {
            parts.add("Blocked " + Schedule.fmt(schedStart(c)) + " to " + Schedule.fmt(schedEnd(c)))
        }
        return if (parts.isEmpty()) "No restriction selected" else parts.joinToString("  +  ")
    }

    // ---------------------------------------------------------------- daily-limit settings
    /** Daily limit per app, minutes. */
    fun limitMin(c: Context) = sp(c).getInt("limit", 30)

    /** How long an app stays locked after the limit is hit, minutes. */
    fun cooldownMin(c: Context) = sp(c).getInt("cooldown", 180)

    /** How long you may use the app after the lock ends, minutes. */
    fun windowMin(c: Context) = sp(c).getInt("window", 10)

    fun setLimits(c: Context, limit: Int, cooldown: Int, window: Int) {
        sp(c).edit()
            .putInt("limit", limit)
            .putInt("cooldown", cooldown)
            .putInt("window", window)
            .apply()
    }

    // ---------------------------------------------------------------- saved posts
    /** Minutes you may view a saved post opened from FocusLock, even while the app is restricted (0 = off). */
    fun savedPassMin(c: Context) = sp(c).getInt("savedPass", 5)

    /** Lock the app if you scroll while viewing a saved post. */
    fun lockOnScroll(c: Context) = sp(c).getBoolean("lockOnScroll", true)

    fun setSaved(c: Context, passMin: Int, lockOnScroll: Boolean) {
        sp(c).edit()
            .putInt("savedPass", passMin)
            .putBoolean("lockOnScroll", lockOnScroll)
            .apply()
    }

    // ---------------------------------------------------------------- break reminders
    fun alertsOn(c: Context) = sp(c).getBoolean("alertsOn", true)

    /** Remind me after this many minutes of continuous social media use. */
    fun sessionAlertMin(c: Context) = sp(c).getInt("sessionAlert", 20)

    /** Remind me each time my total time today grows by this many minutes. */
    fun totalAlertMin(c: Context) = sp(c).getInt("totalAlert", 120)

    fun setAlerts(c: Context, on: Boolean, sessionMin: Int, totalMin: Int) {
        sp(c).edit()
            .putBoolean("alertsOn", on)
            .putInt("sessionAlert", sessionMin)
            .putInt("totalAlert", totalMin)
            .apply()
    }

    // ---------------------------------------------------------------- usage reports
    fun reportsOn(c: Context) = sp(c).getBoolean("reportsOn", true)

    /** Send a report every N minutes (0 = off). */
    fun reportIntervalMin(c: Context) = sp(c).getInt("reportInterval", 180)

    /** Fixed daily report times, minutes since midnight. */
    fun reportTimes(c: Context): List<Int> =
        (sp(c).getString("rep_times", "") ?: "")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..1439 }
            .distinct()
            .sorted()

    fun setReports(c: Context, on: Boolean, intervalMin: Int, times: List<Int>) {
        sp(c).edit()
            .putBoolean("reportsOn", on)
            .putInt("reportInterval", intervalMin)
            .putString("rep_times", times.distinct().sorted().joinToString(","))
            .putLong("rep_last", 0L)
            .apply()
    }

    fun reportLastAt(c: Context) = sp(c).getLong("rep_last", 0L)
    fun setReportLastAt(c: Context, t: Long) = sp(c).edit().putLong("rep_last", t).apply()
    fun reportSlotDay(c: Context, minute: Int) = sp(c).getString("rep_slot_$minute", "") ?: ""
    fun markReportSlot(c: Context, minute: Int, day: String) =
        sp(c).edit().putString("rep_slot_$minute", day).apply()

    // ---------------------------------------------------------------- pause (temporary break)
    /** Length of one pause, minutes. */
    fun pauseMin(c: Context) = sp(c).getInt("pauseMin", 10)

    /** How many pauses are allowed per day (0 = pausing is off). */
    fun pauseMaxPerDay(c: Context) = sp(c).getInt("pauseMax", 2)

    fun setPauseSettings(c: Context, minutes: Int, perDay: Int) {
        sp(c).edit().putInt("pauseMin", minutes).putInt("pauseMax", perDay).apply()
    }

    fun pausedUntil(c: Context) = sp(c).getLong("pause_until", 0L)

    /** True while FocusLock is paused: nothing is blocked. */
    fun pausedNow(c: Context, now: Long = System.currentTimeMillis()): Boolean =
        enabled(c) && pausedUntil(c) > now

    fun pausesUsedToday(c: Context): Int =
        if (sp(c).getString("pause_day", "") == today()) sp(c).getInt("pause_count", 0) else 0

    fun pausesLeft(c: Context): Int = (pauseMaxPerDay(c) - pausesUsedToday(c)).coerceAtLeast(0)

    /** Starts a pause if FocusLock is on, not already paused, and a pause is left today. */
    fun startPause(c: Context, now: Long): Boolean {
        if (!enabled(c) || pausedUntil(c) > now || pausesLeft(c) <= 0) return false
        sp(c).edit()
            .putLong("pause_until", now + pauseMin(c) * 60_000L)
            .putString("pause_day", today())
            .putInt("pause_count", pausesUsedToday(c) + 1)
            .apply()
        return true
    }

    fun endPause(c: Context) = sp(c).edit().putLong("pause_until", 0L).apply()

    // ---------------------------------------------------------------- tracked apps
    fun apps(c: Context): Set<String> =
        sp(c).getStringSet("apps", null)?.toSet() ?: DEFAULT_APPS

    fun setApps(c: Context, apps: Set<String>) =
        sp(c).edit().putStringSet("apps", HashSet(apps)).apply()

    // ---------------------------------------------------------------- per-app lock state
    fun lockedUntil(c: Context, pkg: String) = sp(c).getLong("lock_$pkg", 0L)
    fun windowUntil(c: Context, pkg: String) = sp(c).getLong("win_$pkg", 0L)
    fun pending(c: Context, pkg: String) = sp(c).getBoolean("pend_$pkg", false)
    fun passUntil(c: Context, pkg: String) = sp(c).getLong("pass_$pkg", 0L)

    /** Why an app is locked: "limit", "scroll" or "manual". */
    fun lockReason(c: Context, pkg: String) = sp(c).getString("reason_$pkg", "limit") ?: "limit"

    fun clearPending(c: Context, pkg: String) = sp(c).edit().putBoolean("pend_$pkg", false).apply()

    fun startCooldown(c: Context, pkg: String, now: Long, reason: String = "limit") {
        sp(c).edit()
            .putLong("lock_$pkg", now + cooldownMin(c) * 60_000L)
            .putLong("win_$pkg", 0L)
            .putBoolean("pend_$pkg", true)
            .putString("reason_$pkg", reason)
            .apply()
    }

    fun startWindow(c: Context, pkg: String, now: Long) {
        sp(c).edit()
            .putLong("win_$pkg", now + windowMin(c) * 60_000L)
            .putBoolean("pend_$pkg", false)
            .apply()
    }

    // ---------------------------------------------------------------- saved-post viewing session
    fun viewingPkg(c: Context): String? = sp(c).getString("view_pkg", null)
    fun viewStart(c: Context) = sp(c).getLong("view_start", 0L)

    /** Start viewing a saved post: the app gets a short pass, and scrolling can be detected. */
    fun startViewing(c: Context, pkg: String, now: Long, passMin: Int) {
        sp(c).edit()
            .putString("view_pkg", pkg)
            .putLong("view_start", now)
            .putLong("pass_$pkg", now + passMin * 60_000L)
            .apply()
    }

    fun endViewing(c: Context) {
        val p = viewingPkg(c)
        val ed = sp(c).edit().remove("view_pkg").remove("view_start")
        if (p != null) ed.putLong("pass_$p", 0L)
        ed.apply()
    }

    // ---------------------------------------------------------------- derived state
    fun anyLocked(c: Context): Boolean {
        val now = System.currentTimeMillis()
        return sp(c).all.any { (k, v) -> k.startsWith("lock_") && ((v as? Long) ?: 0L) > now }
    }

    fun lockedCount(c: Context): Int {
        val now = System.currentTimeMillis()
        return sp(c).all.count { (k, v) -> k.startsWith("lock_") && ((v as? Long) ?: 0L) > now }
    }

    /** True while a restriction is being enforced: an app is locked, or blocked hours are on. */
    fun restrictedNow(c: Context): Boolean =
        anyLocked(c) || (enabled(c) && Schedule.isActive(c))

    /** True if any app of this platform is locked or blocked right now. */
    fun platformLocked(c: Context, p: Platform): Boolean {
        if (p == Platform.OTHER) return false
        val now = System.currentTimeMillis()
        if (enabled(c) && Schedule.isActive(c, now)) return true
        return p.packages.any { lockedUntil(c, it) > now }
    }

    /** New day => daily limits and locks start fresh. */
    fun rollDayIfNeeded(c: Context) {
        val today = today()
        val p = sp(c)
        if (p.getString("day", "") == today) return
        val ed = p.edit()
        for (k in p.all.keys) {
            if (k.startsWith("lock_") || k.startsWith("win_") || k.startsWith("pend_") ||
                k.startsWith("reason_")
            ) ed.remove(k)
        }
        ed.putString("day", today).apply()
    }

    // ---------------------------------------------------------------- "My profile"
    fun profileName(c: Context) = sp(c).getString("p_name", "") ?: ""
    fun profileBio(c: Context) = sp(c).getString("p_bio", "") ?: ""
    fun profileContact(c: Context) = sp(c).getString("p_contact", "") ?: ""

    fun saveProfile(c: Context, name: String, bio: String, contact: String) {
        sp(c).edit()
            .putString("p_name", name)
            .putString("p_bio", bio)
            .putString("p_contact", contact)
            .apply()
    }
}
