package com.focuslock.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Hours spent on social media: today, last 7 days, by day and by app. */
class AnalyticsPage(act: MainActivity) : Page(act) {

    private lateinit var chart: BarChartView
    private lateinit var todayV: android.widget.TextView
    private lateinit var weekV: android.widget.TextView
    private lateinit var avgV: android.widget.TextView
    private lateinit var topV: android.widget.TextView
    private lateinit var insight: android.widget.TextView
    private lateinit var byApp: LinearLayout

    override fun build(): View {
        val col = newColumn()
        val (t1, v1) = Ui.statTile(act, "Today")
        val (t2, v2) = Ui.statTile(act, "Last 7 days")
        val (t3, v3) = Ui.statTile(act, "Daily average")
        val (t4, v4) = Ui.statTile(act, "Most used app")
        todayV = v1; weekV = v2; avgV = v3; topV = v4
        topV.textSize = 16f

        val r1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(t1, Ui.lp(act, right = 6, w = 0, weight = 1f))
        r1.addView(t2, Ui.lp(act, left = 6, w = 0, weight = 1f))
        col.addView(r1, Ui.lp(act, bottom = 12))
        val r2 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        r2.addView(t3, Ui.lp(act, right = 6, w = 0, weight = 1f))
        r2.addView(t4, Ui.lp(act, left = 6, w = 0, weight = 1f))
        col.addView(r2, Ui.lp(act, bottom = 12))

        val chartCard = Ui.card(act)
        chartCard.addView(Ui.text(act, "Time on social media, last 7 days", 15f, Ui.TEXT, true))
        chart = BarChartView(act)
        chartCard.addView(chart, Ui.lp(act, top = 8, h = ViewGroup.LayoutParams.WRAP_CONTENT))
        insight = Ui.text(act, "", 13f, Ui.MUTED)
        chartCard.addView(insight, Ui.lp(act, top = 4))
        col.addView(chartCard, Ui.lp(act, bottom = 12))

        val appCard = Ui.card(act)
        appCard.addView(Ui.text(act, "By app, last 7 days", 15f, Ui.TEXT, true))
        byApp = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        appCard.addView(byApp, Ui.lp(act, top = 8))
        col.addView(appCard, Ui.lp(act, bottom = 12))

        col.addView(
            Ui.text(act, "Android keeps detailed usage history for only about a week, so older days may be empty.", 12f, Ui.MUTED)
        )
        return pageScroll(col)
    }

    override fun onShow() = refresh()
    override fun onRefresh() = refresh()

    private fun refresh() {
        if (!Perms.usage(act)) {
            insight.text = "Allow Usage access on the Permissions page to see your analytics."
            return
        }
        val tracked = UsageHelper.installedTracked(act)
        if (tracked.isEmpty()) {
            insight.text = "No tracked apps are installed. Choose apps on the Rules page."
            return
        }
        Thread {
            val set = tracked.toSet()
            val now = System.currentTimeMillis()
            val todayStart = UsageHelper.startOfDay(now)
            val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val values = LongArray(7)
            val labels = Array(7) { "" }
            val perApp = HashMap<String, Long>()
            for (i in 0 until 7) {
                val d = 6 - i
                val cal = Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    add(Calendar.DAY_OF_YEAR, -d)
                }
                val s = cal.timeInMillis
                val e = if (d == 0) now else Calendar.getInstance().apply {
                    timeInMillis = s
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                val m = UsageHelper.usageByPackage(act, s, e, set)
                values[i] = m.values.sum()
                labels[i] = if (d == 0) "Today" else dayFmt.format(Date(s))
                for ((k, v) in m) perApp[k] = (perApp[k] ?: 0L) + v
            }
            act.runOnUiThread { if (act.uiAlive()) show(values, labels, perApp) }
        }.start()
    }

    private fun show(values: LongArray, labels: Array<String>, perApp: Map<String, Long>) {
        val week = values.sum()
        val today = values[6]
        val earlier = (week - today) / 6
        todayV.text = UsageHelper.fmt(today)
        weekV.text = UsageHelper.fmt(week)
        avgV.text = UsageHelper.fmt(week / 7)
        val ranked = perApp.entries.sortedByDescending { it.value }
        topV.text = if (ranked.isNotEmpty() && ranked[0].value > 0) UsageHelper.label(act, ranked[0].key) else "–"

        chart.values = values
        chart.labels = labels
        chart.invalidate()

        insight.text = when {
            earlier <= 0L -> "Not enough earlier days yet to compare."
            today > earlier * 1.2 -> "Today is higher than your average of the previous 6 days (" + UsageHelper.fmt(earlier) + ")."
            today < earlier * 0.8 -> "Today is lower than your average of the previous 6 days (" + UsageHelper.fmt(earlier) + "). Nice."
            else -> "Today is about the same as your previous 6-day average (" + UsageHelper.fmt(earlier) + ")."
        }

        byApp.removeAllViews()
        val max = (ranked.firstOrNull()?.value ?: 0L).coerceAtLeast(1L)
        for (e in ranked) {
            byApp.addView(
                Ui.text(act, UsageHelper.label(act, e.key) + "  ·  " + UsageHelper.fmt(e.value), 14f, Ui.TEXT),
                Ui.lp(act, top = 8)
            )
            byApp.addView(Ui.progress(act, e.value.toFloat() / max, Ui.TEAL_DARK), Ui.lp(act, top = 4, h = dp(8)))
        }
        if (ranked.isEmpty()) byApp.addView(Ui.text(act, "No usage recorded yet.", 14f, Ui.MUTED))
    }
}

/** Simple 7-bar chart drawn with Canvas (today is highlighted in navy). */
class BarChartView(c: Context) : View(c) {
    var values: LongArray = LongArray(0)
    var labels: Array<String> = emptyArray()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE; strokeWidth = 3f }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Ui.dp(context, 210))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = values.size
        if (n == 0) return
        val left = Ui.dp(context, 4).toFloat()
        val right = (width - Ui.dp(context, 4)).toFloat()
        val top = Ui.dp(context, 26).toFloat()
        val bottom = (height - Ui.dp(context, 28)).toFloat()
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(60_000L)
        val slot = (right - left) / n
        val barW = slot * 0.56f
        val radius = Ui.dp(context, 6).toFloat()
        for (i in 0 until n) {
            val x = left + slot * i + (slot - barW) / 2f
            val h = (bottom - top) * (values[i].toFloat() / max)
            barPaint.color = if (i == n - 1) Ui.NAVY else Ui.TEAL_DARK
            canvas.drawRoundRect(x, bottom - h, x + barW, bottom, radius, radius, barPaint)
            textPaint.color = Ui.TEXT
            textPaint.textSize = sp(11f)
            canvas.drawText(UsageHelper.fmt(values[i]), x + barW / 2f, bottom - h - Ui.dp(context, 5), textPaint)
            textPaint.color = Ui.MUTED
            textPaint.textSize = sp(11f)
            val label = if (i < labels.size) labels[i] else ""
            canvas.drawText(label, x + barW / 2f, height - Ui.dp(context, 9).toFloat(), textPaint)
        }
        canvas.drawLine(left, bottom, right, bottom, linePaint)
    }
}
