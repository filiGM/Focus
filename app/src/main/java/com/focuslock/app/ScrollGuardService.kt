package com.focuslock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional "Scroll guard". It listens ONLY for scroll events of TikTok, Facebook, Instagram and YouTube
 * (see res/xml/scroll_guard_config.xml) and ONLY acts while a saved post that was opened from FocusLock is
 * being viewed. It cannot read screen content. If you scroll, the app gets locked.
 */
class ScrollGuardService : AccessibilityService() {

    private var lastFire = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (!Prefs.enabled(this) || !Prefs.lockOnScroll(this)) return
        if (Prefs.pausedNow(this)) return
        val pkg = event.packageName?.toString() ?: return
        val viewing = Prefs.viewingPkg(this) ?: return
        if (pkg != viewing) return
        val now = System.currentTimeMillis()
        if (now >= Prefs.passUntil(this, viewing)) return
        // ignore the app's own start-up movement right after opening the post
        if (now - Prefs.viewStart(this) < GRACE_MS) return
        if (now - lastFire < 2000L) return
        lastFire = now
        lock(viewing, now)
    }

    private fun lock(pkg: String, now: Long) {
        try {
            startForegroundService(
                Intent(this, MonitorService::class.java)
                    .setAction(MonitorService.ACTION_LOCK_NOW)
                    .putExtra("pkg", pkg)
                    .putExtra("reason", "scroll")
            )
        } catch (t: Throwable) {
            // fall back to locking directly
            Prefs.endViewing(this)
            Prefs.startCooldown(this, pkg, now, "scroll")
            try {
                startActivity(
                    Intent(this, LockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("pkg", pkg)
                        .putExtra("until", Prefs.lockedUntil(this, pkg))
                        .putExtra("reason", "scroll")
                )
            } catch (t2: Throwable) {
            }
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val GRACE_MS = 3500L
    }
}
