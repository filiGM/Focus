package com.focuslock.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Every permission FocusLock uses, why it needs it, and a button to grant it. */
class PermissionsPage(act: MainActivity) : Page(act) {

    private class Row(val ok: () -> Boolean, val status: TextView, val action: TextView)

    private lateinit var summary: TextView
    private val rows = ArrayList<Row>()

    override fun build(): View {
        rows.clear()
        val col = newColumn()
        summary = Ui.text(act, "", 14f, Ui.TEXT, true)
        col.addView(summary, Ui.lp(act, bottom = 12))

        addRow(
            col, "Usage access", "Required",
            "Lets FocusLock measure how long each app is on screen. Without it nothing can be limited, counted or reported.",
            { Perms.usage(act) }
        ) { act.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        addRow(
            col, "Display over other apps", "Required",
            "Lets the lock screen appear on top of TikTok, Facebook, Instagram or YouTube when an app is blocked.",
            { Perms.overlay(act) }
        ) {
            act.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + act.packageName))
            )
        }

        addRow(
            col, "Notifications", "Required",
            "Needed for break reminders, limit warnings, usage reports and the \"Lock now\" button.",
            { Perms.notif(act) }
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        addRow(
            col, "Unrestricted battery", "Recommended",
            "Stops Android from putting FocusLock to sleep, which would stop the locking. On Tecno, Infinix, itel and Xiaomi phones also enable Autostart.",
            { Perms.battery(act) }
        ) {
            try {
                act.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + act.packageName))
                )
            } catch (t: Throwable) {
                act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        addRow(
            col, "Scroll guard (Accessibility)", "Optional, needed to lock you out when you scroll a saved post",
            "When you view a saved post from FocusLock, the app should only show that post. Scroll guard notices a scroll in " +
                "TikTok, Facebook, Instagram or YouTube while you are viewing a saved post, and locks the app. " +
                "It cannot read your screen, messages or passwords. Without it, saved posts can't be restricted to viewing only. " +
                "Tap Grant for the exact steps (on Android 13+ the order matters).",
            { Perms.scrollGuard(act) }
        ) { showScrollGuardHelp() }

        addRow(
            col, "Internet", "Automatic",
            "Only used to fetch the title and thumbnail of links you save. Your usage data never leaves the phone.",
            { true }
        ) { }

        return pageScroll(col)
    }

    private fun showScrollGuardHelp() {
        AlertDialog.Builder(act)
            .setTitle("Turn on Scroll guard")
            .setMessage(
                "On Android 13 or newer, an app installed from a file is restricted at first. " +
                    "The \"Allow restricted settings\" option does NOT appear in App info until you have tried to switch the service on once. " +
                    "So do the steps in this order:\n\n" +
                    "1. Tap \"1. Accessibility settings\" below. Find \"FocusLock Scroll guard\" (often under Downloaded apps or Installed apps), tap it, then tap the switch. " +
                    "Android says \"Restricted setting\". Tap OK.\n\n" +
                    "2. Come back here and tap \"2. App info\". Now the ⋮ (three dots) appears at the top right. Tap it, choose \"Allow restricted settings\" and confirm with your PIN or fingerprint.\n\n" +
                    "3. Open Accessibility settings again, tap FocusLock Scroll guard, switch it on and tap Allow.\n\n" +
                    "On Android 12 or older there is nothing to unlock: just switch it on in step 1.\n\n" +
                    "Scroll guard only detects that a scroll happened in TikTok, Facebook, Instagram and YouTube. It cannot read your screen."
            )
            .setPositiveButton("1. Accessibility settings") { _, _ ->
                act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNeutralButton("2. App info") { _, _ ->
                act.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName))
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun addRow(
        col: LinearLayout, title: String, level: String, why: String,
        ok: () -> Boolean, onGrant: () -> Unit
    ) {
        val card = Ui.card(act)
        val head = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            Ui.text(act, title, 16f, Ui.TEXT, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val status = Ui.text(act, "", 13f, Ui.MUTED, true)
        head.addView(status)
        card.addView(head)
        card.addView(Ui.text(act, level, 12f, Ui.MUTED), Ui.lp(act, top = 2))
        card.addView(Ui.text(act, why, 13f, Ui.MUTED), Ui.lp(act, top = 6))
        val action = Ui.button(act, "Grant", small = true) { onGrant() }
        card.addView(action, Ui.lp(act, top = 10, w = ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(card, Ui.lp(act, bottom = 10))
        rows.add(Row(ok, status, action))
    }

    override fun onShow() = update()
    override fun onRefresh() = update()

    private fun update() {
        for (r in rows) {
            val granted = r.ok()
            r.status.text = if (granted) "✅ Granted" else "❌ Not granted"
            r.status.setTextColor(if (granted) Ui.GREEN else Ui.RED)
            r.action.visibility = if (granted) View.GONE else View.VISIBLE
        }
        val missing = Perms.missingRequired(act)
        summary.text = if (missing == 0) "All required permissions are granted." else "$missing required permission(s) still missing."
    }
}
