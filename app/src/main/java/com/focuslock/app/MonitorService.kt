package com.focuslock.app

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Runs in the background (foreground service). Every second it checks which app is on screen.
 *  - Blocked hours: any tracked app is covered by the lock screen.
 *  - Daily limit: over the limit (or still locked) => lock screen on top and the app is stopped.
 *  - Saved-post viewing: a short pass; leaving the app ends it, scrolling (Scroll guard) locks the app.
 *  - Heavy use: break reminders. Time-driven: usage reports.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastQuery = 0L
    private var current: String? = null
    private var lastBlockAt = 0L
    private val usedCache = HashMap<String, Pair<Long, Long>>() // pkg -> (checkedAt, usedMs)

    // saved-post viewing
    private var viewSeen = false

    // pause (temporary break)
    private var wasPaused = false

    // alert state (in memory)
    private var curDay = ""
    private val warned = HashSet<String>()
    private var sessionStart = 0L
    private var lastTrackedSeen = 0L
    private var nextSessionAlert = 0L
    private var lastTotalBucket = -1L
    private var lastTotalCheck = 0L
    private var lastReportCheck = 0L

    private val tick = object : Runnable {
        override fun run() {
            try {
                check()
            } catch (t: Throwable) {
                // never let the loop die
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, Notifier.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle("FocusLock is active")
            .setContentText("Watching your social media time")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LOCK_NOW) {
            val pkg = intent.getStringExtra("pkg")
            val reason = intent.getStringExtra("reason") ?: "manual"
            if (pkg != null) handler.post { lockNow(pkg, reason) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ main loop

    private fun check() {
        if (!Prefs.enabled(this) || !UsageHelper.hasUsageAccess(this)) return
        val now = System.currentTimeMillis()
        Prefs.rollDayIfNeeded(this)
        val day = Prefs.today()
        if (day != curDay) {
            curDay = day
            warned.clear()
            lastTotalBucket = -1L
            usedCache.clear()
        }
        updateForeground(now)
        handleViewing(now)
        checkReports(now)
        if (handlePause(now)) return
        val pkg = current ?: return
        if (pkg !in Prefs.apps(this)) return
        trackSession(pkg, now)
        decide(pkg, now)
        checkTotal(now)
    }

    /** Work out which app is in the foreground from usage events. */
    private fun updateForeground(now: Long) {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = if (lastQuery == 0L) now - 10 * 60_000L else lastQuery
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                1 -> current = e.packageName
                2 -> if (current == e.packageName) current = null
            }
        }
        lastQuery = now - 1500
    }

    /** True while FocusLock is paused (temporary break): nothing is blocked and break reminders are off. */
    private fun handlePause(now: Long): Boolean {
        if (Prefs.pausedNow(this, now)) {
            wasPaused = true
            return true
        }
        if (wasPaused) {
            wasPaused = false
            Notifier.post(
                this, ID_PAUSE, "FocusLock is back on",
                "Your pause is over. Your rules apply again.", force = true
            )
        }
        return false
    }

    /** A saved post is "being viewed" until the pass runs out or you leave that app. */
    private fun handleViewing(now: Long) {
        val vp = Prefs.viewingPkg(this)
        if (vp == null) {
            viewSeen = false
            return
        }
        if (now >= Prefs.passUntil(this, vp)) {
            Prefs.endViewing(this)
            viewSeen = false
            return
        }
        if (current == vp) {
            viewSeen = true
        } else if (viewSeen) {
            Prefs.endViewing(this)       // you left the app: the pass is over
            viewSeen = false
        }
    }

    private fun decide(pkg: String, now: Long) {
        // 0) A saved post was opened from FocusLock: short viewing pass
        if (now < Prefs.passUntil(this, pkg)) return

        // 1) Blocked hours (time-range rule)
        val schedUntil = Schedule.activeUntil(this, now)
        if (schedUntil > 0L) {
            block(pkg, schedUntil, now, "schedule")
            return
        }

        // 2) Locked (daily limit, scroll or manual lock)
        val lockedUntil = Prefs.lockedUntil(this, pkg)
        if (now < lockedUntil) {
            block(pkg, lockedUntil, now, Prefs.lockReason(this, pkg))
            return
        }

        // 3) Daily-limit rule switched off: nothing more to enforce
        if (!Prefs.modeDaily(this)) {
            if (Prefs.pending(this, pkg)) Prefs.clearPending(this, pkg)
            return
        }

        val windowUntil = Prefs.windowUntil(this, pkg)
        // 4) Inside the short unlock window
        if (windowUntil > 0L) {
            if (now < windowUntil) return
            startLock(pkg, now, "limit")                 // window over -> lock again
            return
        }
        // 5) Lock just ended and the app was opened again -> start the window
        if (Prefs.pending(this, pkg)) {
            Prefs.startWindow(this, pkg, now)
            return
        }
        // 6) Normal use: compare today's usage with the daily limit
        val limitMs = Prefs.limitMin(this) * 60_000L
        val used = usedToday(pkg, now)
        if (used >= limitMs) {
            startLock(pkg, now, "limit")
            return
        }
        if (used >= limitMs * 0.8 && warned.add(pkg)) notifyNearLimit(pkg, used)
    }

    private fun startLock(pkg: String, now: Long, reason: String) {
        Prefs.startCooldown(this, pkg, now, reason)
        usedCache.remove(pkg)
        val until = Prefs.lockedUntil(this, pkg)
        notifyLocked(pkg, until, reason)
        block(pkg, until, now, reason)
    }

    private fun lockNow(pkg: String, reason: String) {
        val now = System.currentTimeMillis()
        Prefs.endViewing(this)
        viewSeen = false
        lastBlockAt = 0L
        startLock(pkg, now, reason)
        getSystemService(NotificationManager::class.java).cancel(ID_SESSION)
    }

    private fun usedToday(pkg: String, now: Long): Long {
        val c = usedCache[pkg]
        if (c != null && now - c.first < 5000) return c.second + (now - c.first)
        val u = UsageHelper.usageByPackage(this, UsageHelper.startOfDay(now), now, setOf(pkg))[pkg] ?: 0L
        usedCache[pkg] = Pair(now, u)
        return u
    }

    private fun block(pkg: String, until: Long, now: Long, reason: String) {
        if (now - lastBlockAt < 3000) return
        lastBlockAt = now
        val i = Intent(this, LockActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            .putExtra("pkg", pkg)
            .putExtra("until", until)
            .putExtra("reason", reason)
        startActivity(i)
        // Once the lock screen covers it, also stop the app's process.
        handler.postDelayed({
            try {
                (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                    .killBackgroundProcesses(pkg)
            } catch (t: Throwable) {
            }
        }, 1500)
    }

    // ------------------------------------------------------------------ usage reports

    private fun minuteOfDay(now: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /**
     * Sends the "time spent" report every N minutes and/or at fixed daily times.
     * Reports wait for the screen to be on, so they don't pile up while you sleep.
     */
    private fun checkReports(now: Long) {
        if (!Prefs.reportsOn(this)) return
        if (now - lastReportCheck < 20_000L) return
        lastReportCheck = now
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) return

        var due = false
        val interval = Prefs.reportIntervalMin(this)
        if (interval > 0) {
            val last = Prefs.reportLastAt(this)
            if (last == 0L) Prefs.setReportLastAt(this, now)
            else if (now - last >= interval * 60_000L) due = true
        }
        val today = Prefs.today()
        val m = minuteOfDay(now)
        for (t in Prefs.reportTimes(this)) {
            if (m >= t && m < t + 180 && Prefs.reportSlotDay(this, t) != today) {
                Prefs.markReportSlot(this, t, today)
                due = true
            }
        }
        if (due) {
            Prefs.setReportLastAt(this, now)
            val app = applicationContext
            Thread { Reports.send(app) }.start()
        }
    }

    // ------------------------------------------------------------------ break reminders

    /** Continuous use across tracked apps (gaps under 2 minutes count as the same session). */
    private fun trackSession(pkg: String, now: Long) {
        if (!Prefs.alertsOn(this)) return
        if (now < Prefs.lockedUntil(this, pkg) || Schedule.isActive(this, now)) return
        val step = Prefs.sessionAlertMin(this) * 60_000L
        if (sessionStart == 0L || now - lastTrackedSeen > 120_000L) {
            sessionStart = now
            nextSessionAlert = now + step
        }
        lastTrackedSeen = now
        if (now >= nextSessionAlert) {
            nextSessionAlert += step
            notifySession(pkg, now - sessionStart, now)
        }
    }

    /** Alert each time total time today across all tracked apps grows by the chosen amount. */
    private fun checkTotal(now: Long) {
        if (!Prefs.alertsOn(this)) return
        if (now - lastTotalCheck < 30_000L) return
        lastTotalCheck = now
        val tracked = UsageHelper.installedTracked(this).toSet()
        if (tracked.isEmpty()) return
        val map = UsageHelper.usageByPackage(this, UsageHelper.startOfDay(now), now, tracked)
        val total = map.values.sum()
        val bucket = total / (Prefs.totalAlertMin(this) * 60_000L)
        if (lastTotalBucket < 0L) {
            lastTotalBucket = bucket
        } else if (bucket > lastTotalBucket) {
            lastTotalBucket = bucket
            val top = map.entries.sortedByDescending { it.value }.take(3)
                .joinToString(", ") { UsageHelper.label(this, it.key) + " " + UsageHelper.fmt(it.value) }
            Notifier.post(
                this, ID_TOTAL,
                "${UsageHelper.fmt(total)} on social media today",
                "That's a lot: $top. Consider locking the apps you don't need for the rest of the day."
            )
        }
    }

    private fun notifySession(pkg: String, durMs: Long, now: Long) {
        val name = UsageHelper.label(this, pkg)
        val used = usedToday(pkg, now) / 60_000L
        val daily = Prefs.modeDaily(this)
        val limit = Prefs.limitMin(this)
        val strong = durMs >= 2 * Prefs.sessionAlertMin(this) * 60_000L || (daily && used >= limit * 0.8)
        val advice = if (strong) "FocusLock suggests locking $name now." else "Take a short break."
        val detail = if (daily) {
            val left = (limit - used).coerceAtLeast(0L)
            "Now on $name: $used of $limit min used today, $left min left."
        } else {
            "Now on $name: $used min used today."
        }
        Notifier.post(
            this, ID_SESSION,
            "You've been scrolling for ${durMs / 60_000L} min",
            "$detail $advice",
            pkg
        )
    }

    private fun notifyNearLimit(pkg: String, usedMs: Long) {
        val name = UsageHelper.label(this, pkg)
        val limit = Prefs.limitMin(this)
        Notifier.post(
            this, ID_NEAR + (pkg.hashCode() and 0xFFF),
            "$name is almost at its limit",
            "${usedMs / 60_000L} of $limit min used today. It will be locked at $limit min.",
            pkg
        )
    }

    private fun notifyLocked(pkg: String, until: Long, reason: String) {
        val name = UsageHelper.label(this, pkg)
        val at = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(until))
        val why = when (reason) {
            "scroll" -> "You scrolled while viewing a saved post. "
            "manual" -> "You locked it. "
            else -> ""
        }
        Notifier.post(this, ID_LOCKED + (pkg.hashCode() and 0xFFF), "$name is locked", why + "Locked until $at.")
    }

    companion object {
        const val ACTION_LOCK_NOW = "com.focuslock.app.LOCK_NOW"
        private const val ID_SESSION = 11
        private const val ID_TOTAL = 12
        private const val ID_NEAR = 1000
        private const val ID_LOCKED = 6000
        private const val ID_PAUSE = 14
    }
}
