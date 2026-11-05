package com.focuslock.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Appears as "Save to FocusLock" in the Android share sheet of TikTok, Facebook,
 * Instagram, YouTube and any other app. It stores the link and then closes immediately.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val url = LinkResolver.extractUrl(text)
        if (url == null) {
            toast("No link found to save")
        } else {
            when (SavedStore.addFromUrl(this, url)) {
                AddResult.ADDED -> {
                    toast("Saved to FocusLock")
                    val app = applicationContext
                    Thread { LinkResolver.resolvePending(app) }.start()
                }
                AddResult.DUPLICATE -> toast("Already saved in FocusLock")
                AddResult.LOCKED -> toast("That app is locked right now, so new posts can't be added.")
            }
        }
        finish()
    }

    private fun toast(s: String) = Toast.makeText(applicationContext, s, Toast.LENGTH_LONG).show()
}
