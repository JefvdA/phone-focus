package dev.phonefocus.domain

/**
 * Registry of the apps whose short-video feed (Reels, Shorts, ...) can be blocked on its own,
 * i.e. the only apps for which [AppRule.ShortVideoBlocked] is a valid rule (#1, #5).
 *
 * Hard-coded to start; extend [PACKAGES] when short-video blocking learns a new app.
 */
object ShortVideoApps {
    const val INSTAGRAM: String = "com.instagram.android"
    const val YOUTUBE: String = "com.google.android.youtube"
    const val FACEBOOK: String = "com.facebook.katana"
    const val SNAPCHAT: String = "com.snapchat.android"

    val PACKAGES: Set<String> = setOf(INSTAGRAM, YOUTUBE, FACEBOOK, SNAPCHAT)

    /** True when [AppRule.ShortVideoBlocked] can be applied to [packageName]. */
    fun supports(packageName: String): Boolean = packageName in PACKAGES
}
