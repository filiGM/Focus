package com.focuslock.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** One screen inside the side-panel navigation. */
abstract class Page(val act: MainActivity) {
    abstract fun build(): View
    open fun onShow() {}
    open fun onRefresh() {}
    open fun onHide() {}

    protected fun dp(v: Int): Int = Ui.dp(act, v)

    protected fun newColumn(): LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(32))
    }

    protected fun pageScroll(col: LinearLayout): ScrollView = ScrollView(act).apply { addView(col) }
}

/** Permission checks shared by several pages. */
object Perms {
    fun usage(c: Context) = UsageHelper.hasUsageAccess(c)
    fun overlay(c: Context) = Settings.canDrawOverlays(c)
    fun notif(c: Context) = Build.VERSION.SDK_INT < 33 ||
        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun battery(c: Context) =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    /** Is the optional "Scroll guard" accessibility service switched on? */
    fun scrollGuard(c: Context): Boolean {
        return try {
            val on = Settings.Secure.getInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            if (!on) return false
            val list = Settings.Secure.getString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(c, ScrollGuardService::class.java)
            list.split(':').any { ComponentName.unflattenFromString(it) == me }
        } catch (t: Throwable) {
            false
        }
    }

    /** Required permissions that are still missing (usage access, overlay, notifications). */
    fun missingRequired(c: Context): Int = listOf(usage(c), overlay(c), notif(c)).count { !it }
}
