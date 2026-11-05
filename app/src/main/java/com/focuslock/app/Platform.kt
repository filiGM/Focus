package com.focuslock.app

import android.net.Uri

/** The social networks FocusLock knows how to recognise from a link. */
enum class Platform(
    val label: String,
    val color: Int,
    val packages: List<String>,
    val hosts: List<String>
) {
    TIKTOK(
        "TikTok", 0xFF111111.toInt(),
        listOf("com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill"),
        listOf("tiktok.com")
    ),
    FACEBOOK(
        "Facebook", 0xFF1877F2.toInt(),
        listOf("com.facebook.katana", "com.facebook.lite"),
        listOf("facebook.com", "fb.watch", "fb.com", "fb.me")
    ),
    INSTAGRAM(
        "Instagram", 0xFFC13584.toInt(),
        listOf("com.instagram.android", "com.instagram.lite"),
        listOf("instagram.com", "instagr.am")
    ),
    YOUTUBE(
        "YouTube", 0xFFE02B20.toInt(),
        listOf("com.google.android.youtube"),
        listOf("youtube.com", "youtu.be")
    ),
    OTHER("Other", 0xFF6B7C8C.toInt(), emptyList(), emptyList());

    companion object {
        fun fromUrl(url: String): Platform {
            val host = Uri.parse(url).host?.lowercase() ?: return OTHER
            for (p in values()) {
                if (p.hosts.any { host == it || host.endsWith(".$it") }) return p
            }
            return OTHER
        }
    }
}
