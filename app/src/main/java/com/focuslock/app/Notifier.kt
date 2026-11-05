package com.focuslock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/** Builds and posts all FocusLock notifications. */
object Notifier {
    const val CHANNEL = "focuslock"
    const val ALERT_CHANNEL = "focuslock_alerts"
    const val REPORT_CHANNEL = "focuslock_reports"

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "FocusLock status", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Break reminders & warnings", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(REPORT_CHANNEL, "Usage reports", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /**
     * Posts a notification. Break reminders respect the "Break reminders" switch;
     * pass [force] = true for things the user asked for separately (usage reports).
     */
    fun post(
        c: Context, id: Int, title: String, text: String,
        lockPkg: String? = null, force: Boolean = false, channel: String = ALERT_CHANNEL
    ) {
        if (!force && !Prefs.alertsOn(c)) return
        try {
            ensureChannels(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            val open = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val b = Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
            if (lockPkg != null) {
                val li = Intent(c, MonitorService::class.java)
                    .setAction(MonitorService.ACTION_LOCK_NOW)
                    .putExtra("pkg", lockPkg)
                    .putExtra("reason", "manual")
                val pi = PendingIntent.getService(
                    c, lockPkg.hashCode(), li,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val icon = Icon.createWithResource(c, R.drawable.ic_stat_lock)
                b.addAction(
                    Notification.Action.Builder(icon, "Lock ${UsageHelper.label(c, lockPkg)} now", pi).build()
                )
            }
            nm.notify(id, b.build())
        } catch (t: Throwable) {
            // notifications are best effort
        }
    }
}

/** The "time spent" report: social media time and total phone time for today. */
object Reports {
    private const val ID_REPORT = 13

    /** Builds and posts a report right now. Does network-free but heavier work: call off the main thread. */
    fun send(c: Context) {
        val now = System.currentTimeMillis()
        val start = UsageHelper.startOfDay(now)
        val tracked = UsageHelper.installedTracked(c).toSet()
        val social: Map<String, Long> =
            if (tracked.isEmpty()) emptyMap() else UsageHelper.usageByPackage(c, start, now, tracked)
        val socialTotal = social.values.sum()
        val phone = maxOf(UsageHelper.phoneTotal(c, start, now), socialTotal)
        val top = social.entries.filter { it.value > 0L }
            .sortedByDescending { it.value }.take(3)
            .joinToString(", ") { UsageHelper.label(c, it.key) + " " + UsageHelper.fmt(it.value) }

        val title = "Today so far: " + UsageHelper.fmt(socialTotal) + " on social media"
        val sb = StringBuilder("Phone total: ").append(UsageHelper.fmt(phone))
        if (phone > 0L) {
            val pct = (socialTotal * 100 / phone).toInt().coerceIn(0, 100)
            sb.append(" (social media is ").append(pct).append("% of it)")
        }
        sb.append(".")
        if (top.isNotEmpty()) sb.append(" Most used: ").append(top).append(".")
        Notifier.post(c, ID_REPORT, title, sb.toString(), force = true, channel = Notifier.REPORT_CHANNEL)
    }
}
