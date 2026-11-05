package com.focuslock.app

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finds the title and thumbnail of a saved link.
 * TikTok and YouTube publish an "oEmbed" endpoint. For Facebook, Instagram and anything else
 * we read the public preview tags (og:title, og:image) of the page, like a chat app does.
 */
object LinkResolver {

    private val URL_RE = Regex("https?://[^\\s<>\"']+")
    private const val UA_BOT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private const val UA_BROWSER =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val running = AtomicBoolean(false)

    private class Meta(val title: String?, val thumb: String?)

    fun extractUrl(text: String): String? =
        URL_RE.find(text)?.value?.trimEnd('.', ',', ')', ';', '!')

    /** Resolves every saved item that has no title yet. Returns true if anything changed. */
    fun resolvePending(c: Context): Boolean {
        if (!running.compareAndSet(false, true)) return false
        var changed = false
        try {
            for (item in SavedStore.all(c)) {
                if (item.resolved) continue
                item.attempts++
                val meta = try { fetch(item) } catch (t: Throwable) { null }
                val title = meta?.title
                if (title != null) item.title = title.take(200)
                val thumbUrl = meta?.thumb
                if (!item.hasThumb && thumbUrl != null) {
                    item.hasThumb = try { downloadThumb(c, item.id, thumbUrl) } catch (t: Throwable) { false }
                }
                if (item.title.isBlank() && item.attempts >= 3) item.title = item.platform.label + " post"
                item.resolved = title != null || item.attempts >= 3
                SavedStore.update(c, item)
                changed = true
            }
        } finally {
            running.set(false)
        }
        return changed
    }

    private fun fetch(item: SavedItem): Meta? {
        when (item.platform) {
            Platform.TIKTOK -> {
                val m = oembed("https://www.tiktok.com/oembed?url=", item.url)
                if (m != null && (m.title != null || m.thumb != null)) return m
            }
            Platform.YOUTUBE -> {
                val m = oembed("https://www.youtube.com/oembed?format=json&url=", item.url)
                if (m != null && (m.title != null || m.thumb != null)) return m
            }
            else -> {}
        }
        val html = getText(item.url, UA_BOT) ?: return null
        val title = og(html, "og:title")
            ?: Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.let { unescape(it) }
        val img = og(html, "og:image")
        return Meta(title?.takeIf { it.isNotBlank() }, img?.takeIf { it.isNotBlank() })
    }

    private fun oembed(endpoint: String, url: String): Meta? {
        val body = getText(endpoint + URLEncoder.encode(url, "UTF-8"), UA_BROWSER) ?: return null
        val o = JSONObject(body)
        val title = o.optString("title", "").takeIf { it.isNotBlank() }
        val thumb = o.optString("thumbnail_url", "").takeIf { it.isNotBlank() }
        return Meta(title, thumb)
    }

    // ---- HTTP helpers ----

    private fun open(url: String, ua: String): HttpURLConnection? {
        var cur = url
        for (i in 0 until 6) {
            val con = URL(cur).openConnection() as HttpURLConnection
            con.connectTimeout = 10_000
            con.readTimeout = 10_000
            con.instanceFollowRedirects = false
            con.setRequestProperty("User-Agent", ua)
            con.setRequestProperty("Accept-Language", "en")
            val code = con.responseCode
            if (code in 300..399) {
                val loc = con.getHeaderField("Location") ?: return null
                cur = URL(URL(cur), loc).toString()
                con.disconnect()
                continue
            }
            return if (code in 200..299) con else null
        }
        return null
    }

    private fun readLimited(ins: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
            if (total >= max) break
        }
        return out.toByteArray()
    }

    private fun getText(url: String, ua: String): String? {
        val con = open(url, ua) ?: return null
        return try {
            String(con.inputStream.use { readLimited(it, 700_000) }, Charsets.UTF_8)
        } finally {
            con.disconnect()
        }
    }

    private fun downloadThumb(c: Context, id: String, url: String): Boolean {
        val con = open(url, UA_BROWSER) ?: return false
        try {
            val bytes = con.inputStream.use { readLimited(it, 3_000_000) }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0) return false
            SavedStore.thumbFile(c, id).writeBytes(bytes)
            return true
        } finally {
            con.disconnect()
        }
    }

    // ---- HTML helpers ----

    private fun og(html: String, prop: String): String? {
        val p = Regex.escape(prop)
        val a = Regex(
            "<meta[^>]+(?:property|name)=[\"']$p[\"'][^>]*content=[\"']([^\"']*)[\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)
        if (a != null) return unescape(a)
        val b = Regex(
            "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']$p[\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)
        return b?.let { unescape(it) }
    }

    private fun unescape(s: String): String {
        var r = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">")
        r = Regex("&#(\\d+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull()
                ?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrNull() } ?: m.value
        }
        r = Regex("&#x([0-9a-fA-F]+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull(16)
                ?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrNull() } ?: m.value
        }
        return r.trim()
    }
}
