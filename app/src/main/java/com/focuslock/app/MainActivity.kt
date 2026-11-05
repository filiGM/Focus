package com.focuslock.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Hosts the top bar, the slide-in side panel and whichever page is selected. */
class MainActivity : Activity() {

    enum class Nav(val label: String, val icon: String) {
        HOME("Dashboard", "🏠"),
        SAVED("Saved posts", "🔖"),
        ANALYTICS("Usage analytics", "📊"),
        RULES("Rules & alerts", "⚙️"),
        PERMISSIONS("Permissions", "🔐"),
        LEGAL("Legal notices", "📜"),
        ABOUT("About me", "👤")
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var scrim: View
    private lateinit var titleView: TextView
    private val navViews = HashMap<Nav, TextView>()
    private var panelOpen = false
    private var nav = Nav.HOME
    private var page: Page? = null
    private var destroyed = false

    private val refresher = object : Runnable {
        override fun run() {
            page?.onRefresh()
            handler.postDelayed(this, 5000)
        }
    }

    private fun panelWidth() = Ui.dp(this, 290)

    fun uiAlive(): Boolean = !destroyed && !isFinishing

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // top bar
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.NAVY)
            setPadding(Ui.dp(this@MainActivity, 4), 0, Ui.dp(this@MainActivity, 16), 0)
            elevation = Ui.dp(this@MainActivity, 4).toFloat()
        }
        val menu = TextView(this).apply {
            text = "☰"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@MainActivity, 14), 0, Ui.dp(this@MainActivity, 14), 0)
            isClickable = true
            setOnClickListener { setPanel(true) }
        }
        titleView = Ui.text(this, "", 20f, Color.WHITE, true)
        bar.addView(menu, LinearLayout.LayoutParams(wrap, match))
        bar.addView(titleView, LinearLayout.LayoutParams(0, wrap, 1f))
        bar.addView(
            ImageView(this).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32))
        )
        main.addView(bar, LinearLayout.LayoutParams(match, Ui.dp(this, 56)))

        content = FrameLayout(this)
        main.addView(content, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(main, FrameLayout.LayoutParams(match, match))

        scrim = View(this).apply {
            setBackgroundColor(0x66000000)
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { setPanel(false) }
        }
        root.addView(scrim, FrameLayout.LayoutParams(match, match))

        panel = buildPanel()
        root.addView(panel, FrameLayout.LayoutParams(panelWidth(), match))
        panel.translationX = -panelWidth().toFloat()

        setContentView(root)
        showNav(navFrom(intent))
    }

    private fun buildPanel(): LinearLayout {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.CARD)
            elevation = Ui.dp(this@MainActivity, 16).toFloat()
            isClickable = true
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.NAVY)
            setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 20))
        }
        head.addView(
            ImageView(this).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64))
        )
        head.addView(Ui.text(this, "FocusLock", 22f, Color.WHITE, true), Ui.lp(this, top = 12))
        head.addView(Ui.text(this, "Own your time", 13f, Ui.TEAL))
        p.addView(head, LinearLayout.LayoutParams(match, wrap))

        for (n in Nav.values()) {
            val tv = TextView(this).apply {
                text = n.icon + "   " + n.label
                textSize = 16f
                setTextColor(Ui.TEXT)
                setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 13), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 13))
                isClickable = true
                setOnClickListener {
                    showNav(n)
                    setPanel(false)
                }
            }
            navViews[n] = tv
            p.addView(tv, Ui.lp(this, top = 3, left = 8, right = 8))
        }

        p.addView(View(this), LinearLayout.LayoutParams(match, 0, 1f))
        p.addView(
            Ui.text(this, "Version " + versionName(), 12f, Ui.MUTED),
            Ui.lp(this, left = 20, bottom = 16)
        )
        return p
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (t: Throwable) {
        ""
    }

    private fun setPanel(show: Boolean) {
        panelOpen = show
        if (show) {
            scrim.visibility = View.VISIBLE
            scrim.animate().alpha(1f).setDuration(180).start()
            panel.animate().translationX(0f).setDuration(200).start()
        } else {
            scrim.animate().alpha(0f).setDuration(180).withEndAction {
                if (!panelOpen) scrim.visibility = View.GONE
            }.start()
            panel.animate().translationX(-panelWidth().toFloat()).setDuration(200).start()
        }
    }

    fun navigate(n: Nav) = showNav(n)

    private fun navFrom(i: Intent?): Nav = try {
        Nav.valueOf(i?.getStringExtra("page") ?: "HOME")
    } catch (t: Throwable) {
        Nav.HOME
    }

    private fun showNav(n: Nav) {
        page?.onHide()
        nav = n
        titleView.text = n.label
        val p: Page = when (n) {
            Nav.HOME -> HomePage(this)
            Nav.SAVED -> SavedPage(this)
            Nav.ANALYTICS -> AnalyticsPage(this)
            Nav.RULES -> RulesPage(this)
            Nav.PERMISSIONS -> PermissionsPage(this)
            Nav.LEGAL -> LegalPage(this)
            Nav.ABOUT -> AboutPage(this)
        }
        page = p
        content.removeAllViews()
        content.addView(
            p.build(),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        p.onShow()
        for ((k, v) in navViews) {
            v.background = if (k == n) Ui.bg(this, 0x3300B894, 12) else null
            v.setTypeface(null, if (k == n) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.hasExtra("page") == true) showNav(navFrom(intent))
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.enabled(this)) startMonitor()
        page?.onRefresh()
        handler.postDelayed(refresher, 5000)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            panelOpen -> setPanel(false)
            nav != Nav.HOME -> showNav(Nav.HOME)
            else -> super.onBackPressed()
        }
    }

    fun startMonitor() {
        try {
            startForegroundService(Intent(this, MonitorService::class.java))
        } catch (t: Throwable) {
            toast("Could not start service: " + t.message)
        }
    }

    fun stopMonitor() {
        stopService(Intent(this, MonitorService::class.java))
    }
}
