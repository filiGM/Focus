package com.focuslock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File

/** Tiny image loader for the saved-post thumbnails (no external library needed). */
object ThumbLoader {

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(c: Context, item: SavedItem, iv: ImageView) {
        iv.tag = item.id
        iv.setImageDrawable(null)
        if (!item.hasThumb) return
        val cached = cache.get(item.id)
        if (cached != null) {
            iv.setImageBitmap(cached)
            return
        }
        val app = c.applicationContext
        Thread {
            val bmp = decode(SavedStore.thumbFile(app, item.id), 400)
            if (bmp != null) {
                cache.put(item.id, bmp)
                iv.post { if (iv.tag == item.id) iv.setImageBitmap(bmp) }
            }
        }.start()
    }

    fun forget(id: String) {
        cache.remove(id)
    }

    private fun decode(f: File, target: Int): Bitmap? {
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }
}
