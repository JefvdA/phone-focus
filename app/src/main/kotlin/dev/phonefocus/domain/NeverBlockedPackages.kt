package dev.phonefocus.domain

/**
 * System packages the app blocker (#11) must never send away from, whatever the policy says:
 * bouncing one of them would brick the phone or cut off calls. Not apps the user picks: they
 * are not offered on the desktop and need no [AppRule].
 *
 * This is the fixed, device-independent part. Packages that depend on the device and the
 * user's choices (the current keyboard, the default dialer, the home/recents provider) are
 * resolved by infrastructure and passed in next to this list. Listing a package that is not
 * installed is harmless.
 */
object NeverBlockedPackages {
    /** The Android system server: shutdown/power menu, "choose an app" resolver, crash dialogs. */
    const val ANDROID = "android"

    /** Status bar, notification shade, quick settings, recents on some devices, lock screen. */
    const val SYSTEM_UI = "com.android.systemui"

    /** Calls and emergency calls: telephony, telecom, in-call screens, emergency dialers and alerts. */
    val CALLS: Set<String> = setOf(
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.android.incallui",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.app.telephonyui",
        "com.samsung.android.emergency",
        "com.sec.android.app.safetyassurance",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver.overlay",
    )

    /** System UI: shade, share sheet ("chooser"), lock screen / AOD and One UI overlays. */
    val SYSTEM_SURFACES: Set<String> = setOf(
        ANDROID,
        SYSTEM_UI,
        "com.android.intentresolver",
        "com.samsung.android.app.aodservice",
        "com.samsung.android.biometrics.app.setting",
        "com.samsung.android.app.cocktailbarservice",
    )

    /**
     * Dialogs other apps rely on and that must be answerable: runtime permissions and app
     * install/uninstall confirmations.
     */
    val SYSTEM_DIALOGS: Set<String> = setOf(
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    /** Common keyboards, on top of the current one that infrastructure resolves. */
    val KEYBOARDS: Set<String> = setOf(
        "com.samsung.android.honeyboard",
        "com.google.android.inputmethod.latin",
    )

    /**
     * Home and recents providers, on top of the ones infrastructure resolves. With a third-party
     * home app (this one), recents are still drawn by the system launcher.
     */
    val LAUNCHERS: Set<String> = setOf(
        "com.sec.android.app.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
    )

    /** Every never-blocked package. */
    val packages: Set<String> = CALLS + SYSTEM_SURFACES + SYSTEM_DIALOGS + KEYBOARDS + LAUNCHERS

    operator fun contains(packageName: String): Boolean = packageName in packages
}
