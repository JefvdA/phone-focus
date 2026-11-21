package dev.phonefocus.ui.launcher

import dev.phonefocus.application.ExitCountdown
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.ui.setup.SetupLabels
import java.time.Duration

/** User-facing texts of the focus launcher. */
internal object LauncherLabels {
    /** The taskbar's Focus button: "Focus", or "Exit 23:41" while the exit countdown runs. */
    fun focusButton(remaining: Duration?): String =
        if (remaining == null) "Focus" else "Exit ${ExitCountdown.format(remaining)}"

    fun exitDelay(delay: ExitDelay): String = "Exit delay: ${SetupLabels.exitDelay(delay)}"

    fun exitPending(remaining: Duration): String =
        "Exit requested. The phone unlocks in ${ExitCountdown.format(remaining)} (hours:minutes)."

    const val FOCUS_ON = "Focus mode is on."

    const val FOCUS_OFF =
        "Focus mode is off: your phone is unlocked. Switch the home screen back to One UI Home, " +
            "or start focus mode again."
}
