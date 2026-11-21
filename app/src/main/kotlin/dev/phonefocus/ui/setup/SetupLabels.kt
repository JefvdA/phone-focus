package dev.phonefocus.ui.setup

import dev.phonefocus.application.StartPrerequisite
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.ExitDelay

/** User-facing texts of the focus setup screen. */
internal object SetupLabels {
    fun rule(rule: AppRule): String = when (rule) {
        AppRule.Allowed -> "Allowed"
        AppRule.ShortVideoBlocked -> "No short videos"
        AppRule.Blocked -> "Blocked"
    }

    /** "12 hours", "48 hours", "7 days"; under an hour in minutes (the debug delay). */
    fun exitDelay(delay: ExitDelay): String {
        val d = delay.duration
        return when {
            d.toHours() < 1 -> plural(d.toMinutes(), "minute")
            d.toHours() < 72 || d.toHours() % 24 != 0L -> plural(d.toHours(), "hour")
            else -> plural(d.toDays(), "day")
        }
    }

    fun prerequisite(prerequisite: StartPrerequisite): String = when (prerequisite) {
        StartPrerequisite.DefaultHome ->
            "Set Phone Focus as the default home app, so focus mode replaces the home screen."
        StartPrerequisite.AccessibilityService ->
            "Turn on the Phone Focus app blocker in Accessibility settings. During focus it checks " +
                "which app is in front and sends you back to the focus desktop when it is a blocked " +
                "app. It reads nothing else; the phone, Settings and the keyboard are never blocked."
    }

    fun confirmation(delay: ExitDelay): String =
        "Only the apps you allowed can be opened. While focus mode is on, these settings can " +
            "only be made stricter.\n\nThere is no quick way out: after you ask to exit, the " +
            "phone unlocks only ${exitDelay(delay)} later."

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
}
