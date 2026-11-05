package com.focuslock.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedItem(
    val id: String,
    val url: String,
    val platform: Platform,
    var title: String,
    var resolved: Boolean,
    var attempts: Int,
    var hasThumb: Boolean,
    val addedAt: Long
)

enum class AddResult { ADDED, DUPLICATE, LOCKED }

/** Saved posts, kept in a small JSON file inside the app's private storage. */
object SavedStore {
    private const val FILE = "saved.json"
    private val lock = Any()
    private var counter = 0

    fun all(c: Context): List<SavedItem> = synchronized(lock) { load(c) }

    fun thumbFile(c: Context, id: String): File {
        val dir = File(c.filesDir, "thumbs")
        dir.mkdirs()
        return File(dir, "$id.jpg")
    }

    /** Adds a link unless it is already saved, or its app is locked right now. */
    fun addFromUrl(c: Context, url: String): AddResult = synchronized(lock) {
        val platform = Platform.fromUrl(url)
        if (Prefs.platformLocked(c, platform)) return AddResult.LOCKED
        val list = load(c)
        if (list.any { it.url == url }) return AddResult.DUPLICATE
        counter++
        val id = System.currentTimeMillis().toString() + counter
        list.add(SavedItem(id, url, platform, "", false, 0, false, System.currentTimeMillis()))
        write(c, list)
        AddResult.ADDED
    }

    fun update(c: Context, item: SavedItem) = synchronized(lock) {
        val list = load(c)
        val i = list.indexOfFirst { it.id == item.id }
        if (i >= 0) {
            list[i] = item
            write(c, list)
        }
    }

    fun remove(c: Context, id: String) = synchronized(lock) {
        val list = load(c)
        list.removeAll { it.id == id }
        write(c, list)
        thumbFile(c, id).delete()
    }

    private fun load(c: Context): MutableList<SavedItem> {
        val out = ArrayList<SavedItem>()
        val f = File(c.filesDir, FILE)
        if (!f.exists()) return out
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    SavedItem(
                        o.getString("id"),
                        o.getString("url"),
                        try { Platform.valueOf(o.optString("platform", "OTHER")) } catch (t: Throwable) { Platform.OTHER },
                        o.optString("title", ""),
                        o.optBoolean("resolved", false),
                        o.optInt("attempts", 0),
                        o.optBoolean("thumb", false),
                        o.optLong("addedAt", 0L)
                    )
                )
            }
        } catch (t: Throwable) {
            // unreadable file: start empty rather than crash
        }
        return out
    }

    private fun write(c: Context, list: List<SavedItem>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("url", s.url)
                    .put("platform", s.platform.name)
                    .put("title", s.title)
                    .put("resolved", s.resolved)
                    .put("attempts", s.attempts)
                    .put("thumb", s.hasThumb)
                    .put("addedAt", s.addedAt)
            )
        }
        File(c.filesDir, FILE).writeText(arr.toString())
    }
}
