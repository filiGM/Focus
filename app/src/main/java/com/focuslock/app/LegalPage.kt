package com.focuslock.app

import android.view.View

/** Privacy, permissions, trademarks and disclaimers, in plain language. */
class LegalPage(act: MainActivity) : Page(act) {

    private val sections = listOf(
        "Privacy" to
            "FocusLock has no account, no ads and no analytics. Your usage statistics, rules and saved posts are stored only on this phone and are never uploaded to the developer or to any server. " +
            "Uninstalling the app deletes all of it.",
        "What FocusLock reads" to
            "With your permission, FocusLock reads which app is on screen and for how long, so it can count time, apply limits and send usage reports (including an estimate of your total time on the phone). " +
            "It does not read the content of other apps, your messages, passwords or your accounts. " +
            "It cannot see what you save inside TikTok, Facebook, Instagram or YouTube. Only links you choose to share to FocusLock, or paste into it, are stored.",
        "Scroll guard (Accessibility service)" to
            "Scroll guard is optional and off until you turn it on in Android's Accessibility settings. It is limited to TikTok, Facebook, Instagram and YouTube and receives one kind of event only: that a scroll happened. " +
            "It cannot read text, screen content, messages or passwords, and it does nothing unless you are viewing a saved post that you opened from FocusLock. When you scroll in that situation, FocusLock locks the app. " +
            "Nothing it sees is stored or sent anywhere. You can switch it off at any time in Accessibility settings.",
        "Internet use" to
            "When you save a link, FocusLock contacts that link's own website (for example tiktok.com or youtube.com) to read its public title and thumbnail, the way a chat app shows a link preview. " +
            "That website sees your IP address as it would from any browser. Some posts are private or need a login, so their title or thumbnail may be missing.",
        "Permissions explained" to
            "Usage access: measure time per app and for the phone.\n" +
            "Display over other apps: show the lock screen.\n" +
            "Notifications: break reminders, warnings and usage reports.\n" +
            "Foreground service and boot: keep monitoring running and restart it after a reboot.\n" +
            "Stop background apps: close a blocked app.\n" +
            "Battery optimization: keep FocusLock from being put to sleep.\n" +
            "Accessibility (optional): detect scrolling while a saved post is viewed.\n" +
            "Internet: fetch titles and thumbnails of saved links.",
        "Not affiliated" to
            "TikTok, Facebook, Instagram, YouTube and their logos are trademarks of their respective owners. " +
            "FocusLock is an independent app. It is not made, endorsed or sponsored by any of them.",
        "Your responsibilities" to
            "You are responsible for following the terms of the services you use. Saved links point to content owned by others. " +
            "FocusLock stores links and preview details only; it does not download or copy videos or posts.",
        "No warranty" to
            "FocusLock is provided as is, without warranty. Android may stop background apps, and you can remove its permissions or uninstall it, so limits can never be guaranteed. " +
            "Usage figures are estimates and can differ from other tools such as Digital Wellbeing. " +
            "It is a self-discipline tool, not a medical or therapeutic product. If social media is affecting your health, sleep or mood, please talk to a doctor or counsellor.",
        "Contact" to
            "Developer: Filmon Gebreyesus. Email: Filmongeb8@gmail.com. Phone: +256750272847.",
        "Changes" to
            "This notice may be updated with new versions of the app. Last updated: November 2026."
    )

    override fun build(): View {
        val col = newColumn()
        for ((title, body) in sections) {
            val card = Ui.card(act)
            card.addView(Ui.text(act, title, 16f, Ui.TEXT, true))
            card.addView(Ui.text(act, body, 14f, Ui.MUTED), Ui.lp(act, top = 6))
            col.addView(card, Ui.lp(act, bottom = 10))
        }
        return pageScroll(col)
    }
}
