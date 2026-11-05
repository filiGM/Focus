package com.focuslock.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** About the app, the developer's contact details, and an optional editable profile card. */
class AboutPage(act: MainActivity) : Page(act) {

    private lateinit var nameV: TextView
    private lateinit var bioV: TextView
    private lateinit var contactV: TextView

    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun build(): View {
        val col = newColumn()

        val hero = Ui.card(act, Ui.NAVY).apply { gravity = Gravity.CENTER_HORIZONTAL }
        hero.addView(
            ImageView(act).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams(dp(88), dp(88))
        )
        hero.addView(Ui.text(act, "FocusLock", 24f, android.graphics.Color.WHITE, true), Ui.lp(act, top = 12, w = wrap))
        hero.addView(Ui.text(act, "Own your time", 14f, Ui.TEAL), Ui.lp(act, w = wrap))
        hero.addView(Ui.text(act, "Version " + versionName(), 12f, 0xFF8EA2B5.toInt()), Ui.lp(act, top = 4, w = wrap))
        col.addView(hero, Ui.lp(act, bottom = 12))

        val about = Ui.card(act)
        about.addView(Ui.text(act, "About the app", 16f, Ui.TEXT, true))
        about.addView(
            Ui.text(
                act,
                "FocusLock helps you spend less time on TikTok, Facebook, Instagram and YouTube. " +
                    "It counts your time, blocks an app when your daily limit is reached or during hours you choose, " +
                    "reminds you to take breaks, reports how long you spent on social media and on your phone, " +
                    "and keeps the posts you care about in one place, so you can view them on purpose instead of scrolling.",
                14f, Ui.MUTED
            ),
            Ui.lp(act, top = 6)
        )
        col.addView(about, Ui.lp(act, bottom = 12))

        // ---- developer
        val dev = Ui.card(act)
        dev.addView(Ui.text(act, "Developer", 16f, Ui.TEXT, true))
        dev.addView(Ui.text(act, "Filmon Gebreyesus", 20f, Ui.NAVY, true), Ui.lp(act, top = 8))
        dev.addView(
            contactRow("✉", "Filmongeb8@gmail.com", "Email") {
                launch(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:Filmongeb8@gmail.com"))
                        .putExtra(Intent.EXTRA_SUBJECT, "FocusLock feedback")
                )
            },
            Ui.lp(act, top = 10)
        )
        dev.addView(
            contactRow("📞", "+256750272847", "Call") {
                launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+256750272847")))
            },
            Ui.lp(act, top = 8)
        )
        col.addView(dev, Ui.lp(act, bottom = 12))

        // ---- optional profile
        val me = Ui.card(act)
        val head = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            Ui.text(act, "My profile (optional)", 16f, Ui.TEXT, true),
            LinearLayout.LayoutParams(0, wrap, 1f)
        )
        head.addView(Ui.button(act, "Edit", filled = false, small = true) { edit() })
        me.addView(head)
        nameV = Ui.text(act, "", 18f, Ui.NAVY, true)
        bioV = Ui.text(act, "", 14f, Ui.MUTED)
        contactV = Ui.text(act, "", 14f, Ui.TEXT)
        me.addView(nameV, Ui.lp(act, top = 10))
        me.addView(bioV, Ui.lp(act, top = 4))
        me.addView(contactV, Ui.lp(act, top = 8))
        col.addView(me, Ui.lp(act, bottom = 12))

        col.addView(
            Ui.text(act, "Made to help you focus. Your data stays on your phone.", 12f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        )
        fill()
        return pageScroll(col)
    }

    private fun contactRow(icon: String, text: String, btn: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(act, "$icon   $text", 15f, Ui.TEXT), LinearLayout.LayoutParams(0, wrap, 1f))
            addView(Ui.button(act, btn, filled = false, small = true) { onClick() }, LinearLayout.LayoutParams(wrap, wrap))
        }

    private fun launch(i: Intent) {
        try {
            act.startActivity(i)
        } catch (t: Throwable) {
            act.toast("No app found to do that")
        }
    }

    private fun versionName(): String = try {
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: ""
    } catch (t: Throwable) {
        ""
    }

    private fun fill() {
        val name = Prefs.profileName(act)
        val bio = Prefs.profileBio(act)
        val contact = Prefs.profileContact(act)
        nameV.text = if (name.isBlank()) "Your name" else name
        bioV.text = if (bio.isBlank()) "Tap Edit to add a short bio about yourself." else bio
        contactV.text = if (contact.isBlank()) "" else contact
        contactV.visibility = if (contact.isBlank()) View.GONE else View.VISIBLE
    }

    private fun edit() {
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(act).apply { hint = "Name"; setText(Prefs.profileName(act)) }
        val bio = EditText(act).apply { hint = "Short bio"; setText(Prefs.profileBio(act)); minLines = 2 }
        val contact = EditText(act).apply { hint = "Email or phone (optional)"; setText(Prefs.profileContact(act)) }
        box.addView(name)
        box.addView(bio)
        box.addView(contact)
        AlertDialog.Builder(act)
            .setTitle("My profile")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                Prefs.saveProfile(act, name.text.toString().trim(), bio.text.toString().trim(), contact.text.toString().trim())
                fill()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
