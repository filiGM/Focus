package com.focuslock.app

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Library of posts you saved. Posts arrive through the Android share sheet ("Save to FocusLock")
 * or by pasting a copied link. Titles and thumbnails are fetched from the link's public preview.
 */
class SavedPage(act: MainActivity) : Page(act) {

    private var filter: Platform? = null
    private lateinit var chipsRow: LinearLayout
    private lateinit var countText: TextView
    private lateinit var listBox: LinearLayout
    private var lastSig = ""
    private var resolving = false
    private val dateFmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

    override fun build(): View {
        val col = newColumn()

        val info = Ui.card(act, 0xFFE8FBF5.toInt())
        info.addView(Ui.text(act, "How to save a post", 15f, Ui.TEXT, true))
        info.addView(
            Ui.text(
                act,
                "In TikTok, Facebook, Instagram or YouTube, tap Share and choose \"Save to FocusLock\". " +
                    "The post appears here with its title and thumbnail. You can also copy a link and tap the button below.",
                13f, Ui.MUTED
            ),
            Ui.lp(act, top = 4)
        )
        col.addView(info, Ui.lp(act, bottom = 12))
        col.addView(Ui.button(act, "Paste link from clipboard") { pasteLink() }, Ui.lp(act, bottom = 12))

        val scroller = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false }
        chipsRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        scroller.addView(chipsRow)
        col.addView(scroller, Ui.lp(act, bottom = 8))

        countText = Ui.text(act, "", 13f, Ui.MUTED)
        col.addView(countText, Ui.lp(act, bottom = 8))

        listBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox)
        renderChips()
        return pageScroll(col)
    }

    override fun onShow() {
        render(force = true)
        resolveInBackground()
    }

    override fun onRefresh() {
        render(force = false)
        resolveInBackground()
    }

    private fun resolveInBackground() {
        if (resolving) return
        if (SavedStore.all(act).none { !it.resolved }) return
        resolving = true
        val app = act.applicationContext
        Thread {
            LinkResolver.resolvePending(app)
            act.runOnUiThread {
                resolving = false
                if (act.uiAlive()) render(force = false)
            }
        }.start()
    }

    // ------------------------------------------------------------------ list

    private fun renderChips() {
        chipsRow.removeAllViews()
        val all: List<Platform?> = listOf(null) + Platform.values().toList()
        for (p in all) {
            val selected = filter == p
            val color = p?.color ?: Ui.NAVY
            val tv = TextView(act).apply {
                text = p?.label ?: "All"
                textSize = 13f
                setTextColor(if (selected) android.graphics.Color.WHITE else Ui.TEXT)
                background = if (selected) Ui.bg(act, color, 18) else Ui.bg(act, Ui.CARD, 18, Ui.LINE, 1)
                setPadding(dp(14), dp(7), dp(14), dp(7))
                isClickable = true
                setOnClickListener {
                    filter = p
                    renderChips()
                    render(force = true)
                }
            }
            chipsRow.addView(tv, Ui.lp(act, right = 8, w = ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun render(force: Boolean) {
        val items = SavedStore.all(act)
            .filter { filter == null || it.platform == filter }
            .sortedByDescending { it.addedAt }
        val sig = (filter?.name ?: "ALL") + items.joinToString("|") { it.id + it.resolved + it.hasThumb + it.title.length }
        if (!force && sig == lastSig) return
        lastSig = sig

        countText.text = items.size.toString() + (if (items.size == 1) " saved post" else " saved posts")
        listBox.removeAllViews()
        if (items.isEmpty()) {
            listBox.addView(
                Ui.text(act, "Nothing saved yet.", 14f, Ui.MUTED).apply { gravity = Gravity.CENTER },
                Ui.lp(act, top = 24)
            )
            return
        }
        for (item in items) listBox.addView(itemView(item), Ui.lp(act, bottom = 10))
    }

    private fun itemView(item: SavedItem): View {
        val card = Ui.card(act).apply { orientation = LinearLayout.HORIZONTAL }
        val iv = ImageView(act).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = Ui.bg(act, Ui.LINE, 12)
            clipToOutline = true
            isClickable = true
            setOnClickListener { SavedOpener.open(act, item) }
        }
        card.addView(iv, LinearLayout.LayoutParams(dp(96), dp(96)))
        ThumbLoader.load(act, item, iv)

        val right = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        val titleText = when {
            item.title.isNotBlank() -> item.title
            item.resolved -> item.url
            else -> "Loading title..."
        }
        right.addView(
            Ui.text(act, titleText, 14f, Ui.TEXT, true).apply {
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            }
        )
        val meta = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        meta.addView(Ui.chip(act, item.platform.label, item.platform.color))
        meta.addView(Ui.text(act, dateFmt.format(Date(item.addedAt)), 11f, Ui.MUTED), Ui.lp(act, left = 8, w = ViewGroup.LayoutParams.WRAP_CONTENT))
        right.addView(meta, Ui.lp(act, top = 6))

        val actions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Ui.button(act, "Open", small = true) { SavedOpener.open(act, item) }, Ui.lp(act, right = 8, w = ViewGroup.LayoutParams.WRAP_CONTENT))
        actions.addView(Ui.button(act, "Remove", filled = false, small = true) { confirmRemove(item) }, Ui.lp(act, w = ViewGroup.LayoutParams.WRAP_CONTENT))
        right.addView(actions, Ui.lp(act, top = 8))

        card.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return card
    }

    private fun confirmRemove(item: SavedItem) {
        AlertDialog.Builder(act)
            .setTitle("Remove from FocusLock?")
            .setMessage("This only removes it from FocusLock. Your save inside " + item.platform.label + " is not changed.")
            .setPositiveButton("Remove") { _, _ ->
                SavedStore.remove(act, item.id)
                ThumbLoader.forget(item.id)
                render(force = true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pasteLink() {
        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(act).toString() else ""
        val url = LinkResolver.extractUrl(text)
        if (url == null) {
            act.toast("No link found in the clipboard. Copy a post link first.")
            return
        }
        when (SavedStore.addFromUrl(act, url)) {
            AddResult.ADDED -> {
                act.toast("Saved")
                render(force = true)
                resolveInBackground()
            }
            AddResult.DUPLICATE -> act.toast("Already saved")
            AddResult.LOCKED -> act.toast("That app is locked right now, so new posts can't be added.")
        }
    }
}
