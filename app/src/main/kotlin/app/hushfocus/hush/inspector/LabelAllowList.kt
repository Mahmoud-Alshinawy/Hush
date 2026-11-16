package app.hushfocus.hush.inspector

import app.hushfocus.hush.rules.InstagramRules

/**
 * UI labels the Inspector may store as-is, per app (English, exact match).
 * Temporary home: these move into each app's rules file once those exist (spec §3.6).
 */
object LabelAllowList {
    private val instagram = InstagramRules.inspectorLabels
    private val youtube = setOf("Home", "Shorts", "Subscriptions", "You", "Create", "Search", "Library")
    private val tiktok = setOf(
        "Home", "Friends", "Inbox", "Profile", "For You", "Following", "Explore", "Search", "Shop",
    )
    private val x = setOf("Home", "Search", "Communities", "Notifications", "Messages", "For you", "Following")
    private val snapchat = setOf("Map", "Chat", "Camera", "Stories", "Spotlight", "Profile")
    private val facebook = setOf(
        "Home", "Reels", "Video", "Friends", "Marketplace", "Notifications", "Menu", "Search",
    )

    private val byPackage = mapOf(
        "com.instagram.android" to instagram,
        "com.google.android.youtube" to youtube,
        "com.zhiliaoapp.musically" to tiktok,
        "com.ss.android.ugc.trill" to tiktok,
        "com.twitter.android" to x,
        "com.twitter.android.lite" to x,
        "com.snapchat.android" to snapchat,
        "com.facebook.katana" to facebook,
    )

    fun labelsFor(packageName: String): Set<String> = byPackage[packageName].orEmpty()
}
