package com.focuslock.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.util.Calendar

object UsageHelper {

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun startOfDay(ts: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Foreground milliseconds per package in [start, end].
     * [only] limits the result to those packages; null means every app on the phone.
     */
    fun usageByPackage(c: Context, start: Long, end: Long, only: Set<String>?): Map<String, Long> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        val fgStart = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (only == null && e.eventType == SCREEN_NON_INTERACTIVE) {
                // screen went off: every open span ends here
                for ((p, s) in fgStart) total[p] = (total[p] ?: 0L) + (e.timeStamp - s)
                fgStart.clear()
                continue
            }
            val pkg = e.packageName ?: continue
            if (only != null && pkg !in only) continue
            when (e.eventType) {
                1 -> fgStart[pkg] = e.timeStamp            // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
                2 -> {                                      // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
                    val s = fgStart.remove(pkg)
                    if (s != null) total[pkg] = (total[pkg] ?: 0L) + (e.timeStamp - s)
                }
            }
        }
        if (only == null) {
            // whole-phone mode: only the most recent open span can still be real
            val last = fgStart.entries.maxByOrNull { it.value }
            if (last != null) total[last.key] = (total[last.key] ?: 0L) + (end - last.value)
        } else {
            for ((pkg, s) in fgStart) total[pkg] = (total[pkg] ?: 0L) + (end - s)
        }
        return total
    }

    private const val SCREEN_NON_INTERACTIVE = 16

    /** Packages that are not "phone use": FocusLock itself, system UI and home screens. */
    private fun excludedFromPhoneTotal(c: Context): Set<String> {
        val s = HashSet<String>()
        s.add(c.packageName)
        s.add("com.android.systemui")
        s.add("android")
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            for (r in c.packageManager.queryIntentActivities(home, 0)) s.add(r.activityInfo.packageName)
        } catch (t: Throwable) {
        }
        return s
    }

    private fun looksLikeLauncher(pkg: String): Boolean =
        pkg.contains("launcher", ignoreCase = true) || pkg.endsWith(".home")

    /** Approximate screen time across the whole phone between [start] and [end]. */
    fun phoneTotal(c: Context, start: Long, end: Long): Long {
        val skip = excludedFromPhoneTotal(c)
        return usageByPackage(c, start, end, null)
            .filterKeys { it !in skip && !looksLikeLauncher(it) }
            .values.sum()
    }

    /** Tracked apps that are actually installed on this phone. */
    fun installedTracked(c: Context): List<String> =
        Prefs.apps(c).filter { isInstalled(c, it) }

    fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (t: Throwable) {
        pkg
    }

    fun isInstalled(c: Context, pkg: String): Boolean = try {
        c.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (t: Throwable) {
        false
    }

    fun fmt(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
