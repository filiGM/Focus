package com.focuslock.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Colours and small view builders shared by every screen, so the app looks consistent. */
object Ui {
    val NAVY = 0xFF0F2A43.toInt()
    val TEAL = 0xFF1DE9B6.toInt()
    val TEAL_DARK = 0xFF00B894.toInt()
    val BG = 0xFFF2F5F9.toInt()
    val CARD = 0xFFFFFFFF.toInt()
    val TEXT = 0xFF1B2733.toInt()
    val MUTED = 0xFF6B7C8C.toInt()
    val LINE = 0xFFE3E9EF.toInt()
    val RED = 0xFFE5484D.toInt()
    val AMBER = 0xFFF5A524.toInt()
    val GREEN = 0xFF2BB673.toInt()

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun bg(c: Context, color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(c, strokeDp), strokeColor)
        }

    fun text(c: Context, s: String, sp: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    /** Layout params for a child of a vertical/horizontal LinearLayout. Margins are in dp. */
    fun lp(
        c: Context, top: Int = 0, bottom: Int = 0, left: Int = 0, right: Int = 0,
        w: Int = MATCH, h: Int = WRAP, weight: Float = 0f
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).apply {
            setMargins(dp(c, left), dp(c, top), dp(c, right), dp(c, bottom))
        }

    fun card(c: Context, color: Int = CARD): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(c, color, 16)
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        elevation = dp(c, 2).toFloat()
    }

    fun button(c: Context, label: String, filled: Boolean = true, small: Boolean = false, onClick: () -> Unit): TextView =
        TextView(c).apply {
            text = label
            textSize = if (small) 13f else 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (filled) Color.WHITE else NAVY)
            val base = if (filled) bg(c, NAVY, 12) else bg(c, Color.TRANSPARENT, 12, NAVY, 1)
            background = RippleDrawable(ColorStateList.valueOf(0x33000000), base, null)
            val v = if (small) 8 else 12
            val h = if (small) 14 else 16
            setPadding(dp(c, h), dp(c, v), dp(c, h), dp(c, v))
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun chip(c: Context, label: String, color: Int): TextView = TextView(c).apply {
        text = label
        textSize = 11f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = bg(c, color, 10)
        setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2))
    }

    fun progress(c: Context, frac: Float, color: Int): View {
        val f = frac.coerceIn(0f, 1f)
        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = bg(c, LINE, 4)
            addView(View(c).apply { background = bg(c, color, 4) }, LinearLayout.LayoutParams(0, MATCH, f))
            addView(View(c), LinearLayout.LayoutParams(0, MATCH, 1f - f))
        }
    }

    fun usageColor(frac: Float): Int = when {
        frac >= 0.9f -> RED
        frac >= 0.6f -> AMBER
        else -> GREEN
    }

    fun check(c: Context, label: String, checked: Boolean): android.widget.CheckBox =
        android.widget.CheckBox(c).apply {
            text = label
            textSize = 15f
            setTextColor(TEXT)
            isChecked = checked
            buttonTintList = ColorStateList.valueOf(NAVY)
        }

    fun numberField(c: Context, value: Int): EditText = EditText(c).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value.toString())
        gravity = Gravity.CENTER
        background = bg(c, 0xFFF2F5F9.toInt(), 10, LINE, 1)
        setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8))
    }

    /** "label ........ [field]" row used on the Rules page. */
    fun fieldRow(c: Context, label: String, field: EditText): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(c, label, 14f, TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(field, LinearLayout.LayoutParams(dp(c, 84), WRAP))
    }

    /** Small statistic tile: returns the card and its value TextView so it can be updated. */
    fun statTile(c: Context, label: String): Pair<LinearLayout, TextView> {
        val card = card(c)
        card.addView(text(c, label, 12f, MUTED))
        val value = text(c, "–", 22f, NAVY, true)
        card.addView(value)
        return Pair(card, value)
    }
}
