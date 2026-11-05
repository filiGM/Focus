package com.focuslock.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen "blocked" screen shown on top of a restricted social app. */
class LockActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var until = 0L
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var timer: TextView

    private val tick = object : Runnable {
        override fun run() {
            val left = until - System.currentTimeMillis()
            if (left <= 0) {
                goHome()
                return
            }
            val s = left / 1000
            timer.text = String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 28)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Ui.NAVY)
            setPadding(pad, pad, pad, pad)
        }
        val logo = ImageView(this).apply { setImageResource(R.drawable.logo) }
        col.addView(logo, Ui.lp(this, w = Ui.dp(this, 84), h = Ui.dp(this, 84), bottom = 20))

        title = Ui.text(this, "", 24f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        col.addView(title)
        sub = Ui.text(this, "", 15f, 0xFFB8C7D6.toInt()).apply { gravity = Gravity.CENTER }
        col.addView(sub, Ui.lp(this, top = 12, bottom = 4))
        timer = Ui.text(this, "", 48f, Ui.TEAL, true).apply { gravity = Gravity.CENTER }
        col.addView(timer)
        col.addView(
            Ui.text(
                this,
                "You can still open a saved post from FocusLock, but viewing only: scrolling locks the app.\nGo and do something good for yourself.",
                14f, 0xFF8EA2B5.toInt()
            ).apply { gravity = Gravity.CENTER },
            Ui.lp(this, top = 16, bottom = 28)
        )
        col.addView(
            Ui.button(this, "Go to home screen") { goHome() },
            Ui.lp(this, bottom = 10)
        )
        col.addView(
            Ui.button(this, "Open my saved posts", filled = false) { openSaved() }
        )
        setContentView(col)
        applyIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(i: Intent?) {
        val pkg = i?.getStringExtra("pkg") ?: ""
        val reason = i?.getStringExtra("reason") ?: "limit"
        until = i?.getLongExtra("until", 0L) ?: 0L
        val name = UsageHelper.label(this, pkg)
        when (reason) {
            "schedule" -> {
                title.text = "$name is blocked right now"
                sub.text = "Blocked hours are on. Available again in:"
            }
            "scroll" -> {
                title.text = "$name is locked"
                sub.text = "You scrolled while viewing a saved post. Locked for:"
            }
            "manual" -> {
                title.text = "$name is locked"
                sub.text = "You chose to lock this app. Locked for:"
            }
            else -> {
                title.text = "Time's up for $name"
                sub.text = "Daily limit reached. Locked for:"
            }
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun openSaved() {
        handler.removeCallbacks(tick)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("page", "SAVED")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun goHome() {
        handler.removeCallbacks(tick)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
