package com.focuslock.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri

/**
 * Opens a saved post in its app (or the browser).
 * Viewing mode: while a post is being viewed, the app is usable ONLY for that post. If you scroll
 * (detected by the optional Scroll guard), the app is locked. Leaving the app ends the viewing pass.
 */
object SavedOpener {

    fun open(act: MainActivity, item: SavedItem) {
        val now = System.currentTimeMillis()
        val installed = item.platform.packages.firstOrNull { UsageHelper.isInstalled(act, it) }
        val passMin = Prefs.savedPassMin(act)
        var viewOnly = false

        if (installed != null && passMin > 0) {
            val restricted = !Prefs.pausedNow(act, now) &&
                (Prefs.lockedUntil(act, installed) > now ||
                    (Prefs.enabled(act) && Schedule.isActive(act, now)))
            val guard = Prefs.lockOnScroll(act)
            val guardReady = guard && Perms.scrollGuard(act)

            if (restricted && guard && !guardReady) {
                // Never give a free scrolling pass without a way to enforce "view only".
                AlertDialog.Builder(act)
                    .setTitle("Turn on Scroll guard first")
                    .setMessage(
                        "${UsageHelper.label(act, installed)} is locked. To let you view a saved post while it is " +
                            "locked, FocusLock must be able to detect scrolling, so it can lock you out again if you scroll.\n\n" +
                            "Open the Permissions page and turn on \"Scroll guard\"."
                    )
                    .setPositiveButton("Open Permissions") { _, _ -> act.navigate(MainActivity.Nav.PERMISSIONS) }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
            if (restricted || guardReady) {
                Prefs.startViewing(act, installed, now, passMin)
                viewOnly = guardReady
            }
        }

        val i = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (installed != null) i.setPackage(installed)
        try {
            act.startActivity(i)
            if (viewOnly) act.toast("View only: if you scroll, the app will be locked.")
        } catch (t: Throwable) {
            try {
                i.setPackage(null)
                act.startActivity(i)
            } catch (t2: Throwable) {
                Prefs.endViewing(act)
                act.toast("Could not open this link")
            }
        }
    }
}
