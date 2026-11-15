package app.hushfocus.hush.service

/**
 * Packages whose screens Hush may read (spec §2). Every other app is ignored.
 * Temporary home: to be reconciled with `:core`'s AppId once that exists.
 */
object SupportedApps {
    val packages: Set<String> = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.twitter.android",
        "com.twitter.android.lite",
        "com.snapchat.android",
        "com.facebook.katana",
    )
}
