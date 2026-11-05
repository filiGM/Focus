package com.focuslock.app

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Restriction type, limits, blocked hours, saved-post rules, break reminders, usage reports, tracked apps. */
class RulesPage(act: MainActivity) : Page(act) {

    private lateinit var dailyCb: CheckBox
    private lateinit var schedCb: CheckBox
    private lateinit var dailyCard: LinearLayout
    private lateinit var schedCard: LinearLayout

    private lateinit var limitEt: EditText
    private lateinit var coolEt: EditText
    private lateinit var winEt: EditText

    private lateinit var fromBtn: TextView
    private lateinit var toBtn: TextView
    private lateinit var schedSummary: TextView
    private var from = 0
    private var to = 0

    private lateinit var passEt: EditText
    private lateinit var scrollSw: Switch
    private lateinit var guardWarn: TextView

    private lateinit var alertSw: Switch
    private lateinit var sessEt: EditText
    private lateinit var totalEt: EditText

    private lateinit var repSw: Switch
    private lateinit var repEt: EditText
    private lateinit var timesBox: LinearLayout
    private val times = ArrayList<Int>()

    private lateinit var pauseMinEt: EditText
    private lateinit var pauseMaxEt: EditText

    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun build(): View {
        val col = newColumn()
        from = Prefs.schedStart(act)
        to = Prefs.schedEnd(act)
        times.clear()
        times.addAll(Prefs.reportTimes(act))

        // ---------------- restriction type
        val type = Ui.card(act)
        type.addView(Ui.text(act, "Restriction type", 16f, Ui.TEXT, true))
        type.addView(
            Ui.text(
                act,
                "Choose how your apps get blocked. Tick both to combine them: an app is blocked as soon as either rule applies.",
                13f, Ui.MUTED
            ),
            Ui.lp(act, top = 2, bottom = 8)
        )
        dailyCb = Ui.check(act, "Daily limit", Prefs.modeDaily(act))
        type.addView(dailyCb)
        type.addView(
            Ui.text(act, "Each app gets a time allowance per day. When it runs out the app locks, then opens for a short window, then locks again.", 12f, Ui.MUTED),
            Ui.lp(act, left = 32, bottom = 8)
        )
        schedCb = Ui.check(act, "Time range (blocked hours)", Prefs.modeSchedule(act))
        type.addView(schedCb)
        type.addView(
            Ui.text(act, "All selected apps are blocked between a start and an end time, every day.", 12f, Ui.MUTED),
            Ui.lp(act, left = 32)
        )
        dailyCb.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        schedCb.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        col.addView(type, Ui.lp(act, bottom = 12))

        // ---------------- daily limit settings
        dailyCard = Ui.card(act)
        dailyCard.addView(Ui.text(act, "Daily limit settings", 16f, Ui.TEXT, true))
        limitEt = Ui.numberField(act, Prefs.limitMin(act))
        coolEt = Ui.numberField(act, Prefs.cooldownMin(act))
        winEt = Ui.numberField(act, Prefs.windowMin(act))
        dailyCard.addView(Ui.fieldRow(act, "Daily limit per app (min)", limitEt), Ui.lp(act, top = 10, bottom = 8))
        dailyCard.addView(Ui.fieldRow(act, "Locked for (min)", coolEt), Ui.lp(act, bottom = 8))
        dailyCard.addView(Ui.fieldRow(act, "Then allowed for (min)", winEt))
        col.addView(dailyCard, Ui.lp(act, bottom = 12))

        // ---------------- blocked hours
        schedCard = Ui.card(act)
        schedCard.addView(Ui.text(act, "Blocked hours", 16f, Ui.TEXT, true))
        schedCard.addView(
            Ui.text(act, "The range can cross midnight, for example 22:00 to 06:00.", 13f, Ui.MUTED),
            Ui.lp(act, top = 2, bottom = 10)
        )
        fromBtn = Ui.button(act, Schedule.fmt(from), filled = false, small = true) { pick(true) }
        toBtn = Ui.button(act, Schedule.fmt(to), filled = false, small = true) { pick(false) }
        schedCard.addView(timeRow("Blocked from", fromBtn), Ui.lp(act, bottom = 8))
        schedCard.addView(timeRow("Blocked until", toBtn), Ui.lp(act, bottom = 8))
        schedSummary = Ui.text(act, "", 13f, Ui.MUTED)
        schedCard.addView(schedSummary)
        col.addView(schedCard, Ui.lp(act, bottom = 12))

        // ---------------- saved posts
        val saved = Ui.card(act)
        saved.addView(Ui.text(act, "Saved posts", 16f, Ui.TEXT, true))
        saved.addView(
            Ui.text(act, "Opening a saved post from FocusLock gives you a short viewing pass, even while the app is blocked.", 13f, Ui.MUTED),
            Ui.lp(act, top = 2, bottom = 10)
        )
        passEt = Ui.numberField(act, Prefs.savedPassMin(act))
        saved.addView(Ui.fieldRow(act, "Viewing pass (min, 0 = off)", passEt), Ui.lp(act, bottom = 8))
        scrollSw = Switch(act).apply {
            text = "Lock the app if I scroll while viewing a saved post"
            isChecked = Prefs.lockOnScroll(act)
        }
        saved.addView(scrollSw)
        guardWarn = Ui.text(act, "", 12f, Ui.RED)
        saved.addView(guardWarn, Ui.lp(act, top = 6))
        col.addView(saved, Ui.lp(act, bottom = 12))

        // ---------------- break reminders
        val alerts = Ui.card(act)
        alerts.addView(Ui.text(act, "Break reminders", 16f, Ui.TEXT, true))
        alertSw = Switch(act).apply {
            text = "Notify me about heavy use"
            isChecked = Prefs.alertsOn(act)
        }
        alerts.addView(alertSw, Ui.lp(act, top = 6, bottom = 8))
        sessEt = Ui.numberField(act, Prefs.sessionAlertMin(act))
        totalEt = Ui.numberField(act, Prefs.totalAlertMin(act))
        alerts.addView(Ui.fieldRow(act, "Remind after non-stop use (min)", sessEt), Ui.lp(act, bottom = 8))
        alerts.addView(Ui.fieldRow(act, "Heavy-use alert when my total grows by (min)", totalEt))
        alerts.addView(
            Ui.text(act, "You also get a warning at 80% of an app's daily limit. Reminders include a button to lock the app right away.", 12f, Ui.MUTED),
            Ui.lp(act, top = 6)
        )
        col.addView(alerts, Ui.lp(act, bottom = 12))

        // ---------------- usage reports
        val rep = Ui.card(act)
        rep.addView(Ui.text(act, "Usage report notifications", 16f, Ui.TEXT, true))
        rep.addView(
            Ui.text(act, "A notification with your time on social media and your total time on the phone today.", 13f, Ui.MUTED),
            Ui.lp(act, top = 2, bottom = 6)
        )
        repSw = Switch(act).apply {
            text = "Send usage reports"
            isChecked = Prefs.reportsOn(act)
        }
        rep.addView(repSw, Ui.lp(act, bottom = 8))
        repEt = Ui.numberField(act, Prefs.reportIntervalMin(act))
        rep.addView(Ui.fieldRow(act, "Send every (min, 0 = off)", repEt), Ui.lp(act, bottom = 4))
        rep.addView(Ui.text(act, "180 means every 3 hours.", 12f, Ui.MUTED), Ui.lp(act, bottom = 10))
        rep.addView(Ui.text(act, "Also at these times every day", 14f, Ui.TEXT, true))
        timesBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        rep.addView(timesBox, Ui.lp(act, bottom = 8))
        val repBtns = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        repBtns.addView(
            Ui.button(act, "Add a time", filled = false, small = true) { addTime() },
            Ui.lp(act, right = 8, w = wrap)
        )
        repBtns.addView(
            Ui.button(act, "Send a test report", filled = false, small = true) { testReport() },
            Ui.lp(act, w = wrap)
        )
        rep.addView(repBtns)
        rep.addView(
            Ui.text(act, "Reports are sent while Focus mode is on and the screen is on. The phone total is an estimate and can differ a little from Digital Wellbeing.", 12f, Ui.MUTED),
            Ui.lp(act, top = 8)
        )
        col.addView(rep, Ui.lp(act, bottom = 12))

        // ---------------- pause
        val pause = Ui.card(act)
        pause.addView(Ui.text(act, "Pause FocusLock (temporary break)", 16f, Ui.TEXT, true))
        pause.addView(
            Ui.text(act, "The Pause button on the Dashboard switches blocking off for a short time, then turns it back on by itself.", 13f, Ui.MUTED),
            Ui.lp(act, top = 2, bottom = 10)
        )
        pauseMinEt = Ui.numberField(act, Prefs.pauseMin(act))
        pauseMaxEt = Ui.numberField(act, Prefs.pauseMaxPerDay(act))
        pause.addView(Ui.fieldRow(act, "Minutes allowed per pause", pauseMinEt), Ui.lp(act, bottom = 8))
        pause.addView(Ui.fieldRow(act, "Pauses allowed per day (0 = off)", pauseMaxEt))
        pause.addView(
            Ui.text(act, "While paused nothing is blocked and break reminders are off, but your time still counts toward the daily limit, and usage reports continue.", 12f, Ui.MUTED),
            Ui.lp(act, top = 6)
        )
        col.addView(pause, Ui.lp(act, bottom = 12))

        // ---------------- buttons
        col.addView(Ui.button(act, "Save") { save() }, Ui.lp(act, bottom = 8))
        col.addView(Ui.button(act, "Choose apps to limit", filled = false) { chooseApps() }, Ui.lp(act, bottom = 8))
        col.addView(
            Ui.text(act, "Rules can't be changed while an app is locked or blocked hours are active.", 12f, Ui.MUTED)
        )

        updateVisibility()
        refreshSched()
        renderTimes()
        updateGuardWarn()
        return pageScroll(col)
    }

    override fun onShow() = updateGuardWarn()
    override fun onRefresh() = updateGuardWarn()

    // ---------------------------------------------------------------- small helpers

    private fun timeRow(label: String, btn: TextView): LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Ui.text(act, label, 14f, Ui.TEXT), LinearLayout.LayoutParams(0, wrap, 1f))
        addView(btn, LinearLayout.LayoutParams(wrap, wrap))
    }

    private fun updateVisibility() {
        dailyCard.visibility = if (dailyCb.isChecked) View.VISIBLE else View.GONE
        schedCard.visibility = if (schedCb.isChecked) View.VISIBLE else View.GONE
    }

    private fun updateGuardWarn() {
        if (!::guardWarn.isInitialized) return
        val missing = !Perms.scrollGuard(act)
        guardWarn.visibility = if (missing) View.VISIBLE else View.GONE
        guardWarn.text = "Scroll guard is off, so scrolling can't be detected. Turn it on in the Permissions page."
    }

    private fun refreshSched() {
        fromBtn.text = Schedule.fmt(from)
        toBtn.text = Schedule.fmt(to)
        schedSummary.text = if (from == to) {
            "From and until are the same, so nothing would be blocked."
        } else {
            val len = Schedule.lengthMinutes(from, to)
            val overnight = if (to < from) ", overnight" else ""
            "Blocked from " + Schedule.fmt(from) + " to " + Schedule.fmt(to) +
                " (" + (len / 60) + "h " + (len % 60) + "m" + overnight + ")"
        }
    }

    private fun pick(isFrom: Boolean) {
        val cur = if (isFrom) from else to
        TimePickerDialog(act, { _, h, m ->
            val v = h * 60 + m
            if (isFrom) from = v else to = v
            refreshSched()
        }, cur / 60, cur % 60, true).show()
    }

    private fun renderTimes() {
        timesBox.removeAllViews()
        if (times.isEmpty()) {
            timesBox.addView(Ui.text(act, "No fixed times set.", 13f, Ui.MUTED), Ui.lp(act, top = 4))
            return
        }
        for (t in times.sorted()) {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(Ui.text(act, "🕒  " + Schedule.fmt(t), 15f, Ui.TEXT), LinearLayout.LayoutParams(0, wrap, 1f))
            row.addView(
                Ui.button(act, "Remove", filled = false, small = true) {
                    times.remove(t)
                    renderTimes()
                },
                LinearLayout.LayoutParams(wrap, wrap)
            )
            timesBox.addView(row, Ui.lp(act, top = 6))
        }
    }

    private fun addTime() {
        if (times.size >= 6) {
            act.toast("You can set up to 6 report times.")
            return
        }
        TimePickerDialog(act, { _, h, m ->
            val v = h * 60 + m
            if (v !in times) times.add(v)
            renderTimes()
        }, 12, 0, true).show()
    }

    private fun testReport() {
        if (!Perms.notif(act)) {
            act.toast("Allow Notifications on the Permissions page first.")
            return
        }
        if (!Perms.usage(act)) {
            act.toast("Allow Usage access on the Permissions page first.")
            return
        }
        val app = act.applicationContext
        Thread { Reports.send(app) }.start()
        act.toast("Test report sent. Check your notifications.")
    }

    private fun num(e: EditText, def: Int, min: Int = 1): Int =
        e.text.toString().toIntOrNull()?.coerceAtLeast(min) ?: def

    // ---------------------------------------------------------------- save

    private fun save() {
        if (Prefs.restrictedNow(act)) {
            act.toast("Rules can't be changed while an app is locked or blocked hours are active.")
            return
        }
        val daily = dailyCb.isChecked
        val sched = schedCb.isChecked
        if (!daily && !sched) {
            act.toast("Choose at least one restriction type: daily limit, time range, or both.")
            return
        }
        if (sched && from == to) {
            act.toast("Blocked hours: the start and end time can't be the same.")
            return
        }
        val warn = StringBuilder()
        if (sched) {
            val until = Schedule.endOfActive(from, to, System.currentTimeMillis())
            if (until > 0L && Prefs.enabled(act)) {
                warn.append("These blocked hours are active right now, until ")
                    .append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(until)))
                    .append(". Your apps will be blocked immediately, and you won't be able to change the rules or switch FocusLock off until then.")
            } else if (Schedule.lengthMinutes(from, to) > 12 * 60) {
                warn.append("This blocks your apps for more than 12 hours every day, and rules can't be changed while it is active.")
            }
        }
        if (warn.isEmpty()) {
            commit()
        } else {
            AlertDialog.Builder(act)
                .setTitle("Please confirm")
                .setMessage(warn.toString())
                .setPositiveButton("Save anyway") { _, _ -> commit() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun commit() {
        Prefs.setModes(act, dailyCb.isChecked, schedCb.isChecked, from, to)
        Prefs.setLimits(act, num(limitEt, 30), num(coolEt, 180), num(winEt, 10))
        Prefs.setSaved(act, num(passEt, 5, min = 0), scrollSw.isChecked)
        Prefs.setAlerts(act, alertSw.isChecked, num(sessEt, 20), num(totalEt, 120))
        var interval = num(repEt, 180, min = 0)
        if (interval in 1..9) interval = 10
        Prefs.setReports(act, repSw.isChecked, interval, times)
        Prefs.setPauseSettings(
            act,
            num(pauseMinEt, 10).coerceAtMost(240),
            num(pauseMaxEt, 2, min = 0).coerceAtMost(20)
        )
        act.toast("Saved: " + Prefs.rulesSummary(act))
    }

    // ---------------------------------------------------------------- tracked apps

    private fun chooseApps() {
        if (Prefs.restrictedNow(act)) {
            act.toast("Apps can't be changed while an app is locked or blocked hours are active.")
            return
        }
        val pm = act.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val items = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != act.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.getDefault()) }
        val current = Prefs.apps(act)
        val checked = BooleanArray(items.size) { items[it].first in current }
        AlertDialog.Builder(act)
            .setTitle("Apps to limit and lock")
            .setMultiChoiceItems(items.map { it.second }.toTypedArray(), checked) { _, i, v ->
                checked[i] = v
            }
            .setPositiveButton("Save") { _, _ ->
                val sel = HashSet<String>()
                items.forEachIndexed { i, p -> if (checked[i]) sel.add(p.first) }
                Prefs.setApps(act, sel)
                act.toast("Saved " + sel.size + " apps")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
