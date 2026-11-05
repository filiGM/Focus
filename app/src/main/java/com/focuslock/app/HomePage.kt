package com.focuslock.app

import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dashboard: on/off switch, active rules, today's total and one progress card per tracked app. */
class HomePage(act: MainActivity) : Page(act) {

    private lateinit var statusText: TextView
    private lateinit var rulesText: TextView
    private lateinit var sw: Switch
    private lateinit var todayValue: TextView
    private lateinit var lockedValue: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var appsBox: LinearLayout
    private lateinit var pauseStatus: TextView
    private lateinit var pauseBox: LinearLayout
    private var suppress = false

    override fun build(): View {
        val col = newColumn()

        // hero card with the main switch
        val hero = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.bg(act, Ui.NAVY, 20)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = dp(3).toFloat()
        }
        hero.addView(
            ImageView(act).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams(dp(56), dp(56))
        )
        val mid = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        mid.addView(Ui.text(act, "Focus mode", 18f, Color.WHITE, true))
        statusText = Ui.text(act, "", 13f, Ui.TEAL)
        mid.addView(statusText)
        hero.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sw = Switch(act).apply {
            setOnCheckedChangeListener { _, on -> if (!suppress) toggle(on) }
        }
        hero.addView(sw)
        col.addView(hero, Ui.lp(act, bottom = 8))

        rulesText = Ui.text(act, "", 13f, Ui.MUTED)
        col.addView(rulesText, Ui.lp(act, left = 4, bottom = 12))

        // pause card
        val pauseCard = Ui.card(act)
        pauseCard.addView(Ui.text(act, "Take a short break", 16f, Ui.TEXT, true))
        pauseStatus = Ui.text(act, "", 13f, Ui.MUTED)
        pauseCard.addView(pauseStatus, Ui.lp(act, top = 2, bottom = 10))
        pauseBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        pauseCard.addView(pauseBox)
        col.addView(pauseCard, Ui.lp(act, bottom = 12))

        // setup warning
        setupCard = Ui.card(act, 0xFFFFF4DB.toInt())
        setupCard.visibility = View.GONE
        setupCard.addView(Ui.text(act, "Finish setup", 16f, Ui.TEXT, true))
        setupCard.addView(
            Ui.text(act, "Some permissions are missing, so locking and reminders may not work.", 13f, Ui.MUTED),
            Ui.lp(act, top = 2)
        )
        setupCard.addView(
            Ui.button(act, "Open permissions", small = true) { act.navigate(MainActivity.Nav.PERMISSIONS) },
            Ui.lp(act, top = 10)
        )
        col.addView(setupCard, Ui.lp(act, bottom = 12))

        // tiles
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val (t1, v1) = Ui.statTile(act, "Today on social media")
        val (t2, v2) = Ui.statTile(act, "Apps blocked now")
        todayValue = v1
        lockedValue = v2
        row.addView(t1, Ui.lp(act, right = 6, w = 0, weight = 1f))
        row.addView(t2, Ui.lp(act, left = 6, w = 0, weight = 1f))
        col.addView(row, Ui.lp(act, bottom = 8))

        col.addView(Ui.text(act, "Your apps today", 16f, Ui.TEXT, true), Ui.lp(act, top = 8, bottom = 8))
        appsBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(appsBox)
        return pageScroll(col)
    }

    override fun onShow() = refresh()
    override fun onRefresh() = refresh()

    private fun toggle(on: Boolean) {
        if (!on && Prefs.restrictedNow(act)) {
            act.toast("An app is locked or blocked hours are active. You can't switch FocusLock off right now.")
            suppress = true
            sw.isChecked = true
            suppress = false
            return
        }
        Prefs.setEnabled(act, on)
        if (on) act.startMonitor() else act.stopMonitor()
        refresh()
    }

    private fun refresh() {
        val enabled = Prefs.enabled(act)
        suppress = true
        sw.isChecked = enabled
        suppress = false
        statusText.text = if (enabled) "Active: rules are being enforced" else "Paused: nothing is being locked"
        rulesText.text = "Rules: " + Prefs.rulesSummary(act)
        refreshPause(enabled)
        setupCard.visibility = if (Perms.missingRequired(act) > 0) View.VISIBLE else View.GONE

        val paused = Prefs.pausedNow(act)
        val scheduleOn = enabled && Schedule.isActive(act)
        lockedValue.text = if (paused) {
            "0"
        } else if (scheduleOn) {
            UsageHelper.installedTracked(act).size.toString()
        } else {
            Prefs.lockedCount(act).toString()
        }

        if (!Perms.usage(act)) {
            todayValue.text = "–"
            appsBox.removeAllViews()
            appsBox.addView(
                Ui.text(act, "Allow Usage access (Permissions page) to see your time.", 14f, Ui.MUTED)
            )
            return
        }
        val tracked = UsageHelper.installedTracked(act)
        Thread {
            val now = System.currentTimeMillis()
            val today = UsageHelper.usageByPackage(act, UsageHelper.startOfDay(now), now, tracked.toSet())
            act.runOnUiThread { if (act.uiAlive()) showApps(tracked, today, now) }
        }.start()
    }

    private fun refreshPause(enabled: Boolean) {
        val now = System.currentTimeMillis()
        val until = Prefs.pausedUntil(act)
        pauseBox.removeAllViews()
        if (enabled && until > now) {
            pauseStatus.text = "FocusLock is paused until " + hm(until) + " (" +
                UsageHelper.fmt(until - now + 59_000L) + " left). Nothing is blocked right now."
            pauseBox.addView(Ui.button(act, "Resume FocusLock now") {
                Prefs.endPause(act)
                refresh()
            })
            return
        }
        val total = Prefs.pauseMaxPerDay(act)
        val left = Prefs.pausesLeft(act)
        if (total == 0) {
            pauseStatus.text = "Pausing is turned off. You can allow it in Rules & alerts."
            return
        }
        pauseStatus.text = "$left of $total pauses left today, " + Prefs.pauseMin(act) +
            " min each. FocusLock turns back on by itself."
        val b = Ui.button(act, "Pause FocusLock for " + Prefs.pauseMin(act) + " min", filled = false) {
            if (!Prefs.enabled(act)) act.toast("Switch Focus mode on first.") else confirmPause()
        }
        if (left == 0) {
            b.isEnabled = false
            b.alpha = 0.5f
        } else if (!enabled) {
            b.alpha = 0.5f
        }
        pauseBox.addView(b)
    }

    private fun confirmPause() {
        val left = Prefs.pausesLeft(act)
        AlertDialog.Builder(act)
            .setTitle("Pause FocusLock?")
            .setMessage(
                "Nothing will be blocked for " + Prefs.pauseMin(act) + " minutes, then FocusLock turns back on by itself. " +
                    "Your time still counts toward the daily limit. You have " + left + " pause(s) left today."
            )
            .setPositiveButton("Pause") { _, _ ->
                if (Prefs.startPause(act, System.currentTimeMillis())) {
                    act.toast("FocusLock paused for " + Prefs.pauseMin(act) + " minutes")
                } else {
                    act.toast("No pauses left today.")
                }
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    private fun showApps(tracked: List<String>, today: Map<String, Long>, now: Long) {
        todayValue.text = UsageHelper.fmt(today.values.sum())
        appsBox.removeAllViews()
        if (tracked.isEmpty()) {
            appsBox.addView(Ui.text(act, "No tracked apps are installed. Choose apps on the Rules page.", 14f, Ui.MUTED))
            return
        }
        val dailyOn = Prefs.modeDaily(act)
        val limitMs = Prefs.limitMin(act) * 60_000L
        val schedUntil = if (Prefs.enabled(act)) Schedule.activeUntil(act, now) else 0L
        val pausedUntil = if (Prefs.pausedNow(act, now)) Prefs.pausedUntil(act) else 0L
        for (p in tracked.sortedByDescending { today[it] ?: 0L }) {
            val used = today[p] ?: 0L
            val frac = if (limitMs > 0) used.toFloat() / limitMs else 0f
            val card = Ui.card(act)
            val head = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(
                Ui.text(act, UsageHelper.label(act, p), 16f, Ui.TEXT, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            val usedText = if (dailyOn) {
                UsageHelper.fmt(used) + " / " + Prefs.limitMin(act) + "m"
            } else {
                UsageHelper.fmt(used)
            }
            head.addView(Ui.text(act, usedText, 14f, Ui.MUTED))
            card.addView(head)
            if (dailyOn) {
                card.addView(Ui.progress(act, frac, Ui.usageColor(frac)), Ui.lp(act, top = 8, h = dp(8)))
            }
            val lock = Prefs.lockedUntil(act, p)
            val win = Prefs.windowUntil(act, p)
            val chipLp = Ui.lp(act, top = 8, w = ViewGroup.LayoutParams.WRAP_CONTENT)
            if (pausedUntil > 0L) {
                card.addView(Ui.chip(act, "⏸ Paused until " + hm(pausedUntil), Ui.AMBER), chipLp)
            } else if (schedUntil > 0L) {
                card.addView(Ui.chip(act, "⏰ Blocked hours, until " + hm(schedUntil), Ui.RED), chipLp)
            } else if (lock > now) {
                card.addView(Ui.chip(act, "🔒 Locked, " + UsageHelper.fmt(lock - now) + " left", Ui.RED), chipLp)
            } else if (dailyOn && win > now) {
                card.addView(Ui.chip(act, "⏳ Unlocked, " + UsageHelper.fmt(win - now) + " left", Ui.AMBER), chipLp)
            }
            appsBox.addView(card, Ui.lp(act, bottom = 10))
        }
    }
}
