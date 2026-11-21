package dev.phonefocus.ui.launcher

import dev.phonefocus.application.ExitCountdown
import dev.phonefocus.application.MakeStricterForm
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusError
import dev.phonefocus.domain.FocusState
import dev.phonefocus.domain.Loosening
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

    // --- Make stricter (C4, #9) ------------------------------------------------------------

    const val STRICTER_WARNING = "Changes can't be undone until focus mode ends and the phone is unlocked."

    const val STRICTER_PENDING_NOTE =
        "Exit is already requested: a longer delay applies from your next exit request, the " +
            "running countdown keeps its time."

    const val STRICTER_NO_APPS = "No apps are allowed right now, so only the exit delay can be made longer."

    const val STRICTER_NO_CHANGES = "Nothing changed yet."

    /** One line per change in [form], e.g. "Instagram: Allowed → Blocked". */
    fun stricterChanges(form: MakeStricterForm): List<String> = buildList {
        if (form.delayChanged) {
            add("Exit delay: ${SetupLabels.exitDelay(form.currentDelay)} → ${SetupLabels.exitDelay(form.exitDelay)}")
        }
        form.changedApps.forEach { add("${it.app.label}: ${SetupLabels.rule(it.current)} → ${SetupLabels.rule(it.rule)}") }
    }

    fun stricterConfirmation(form: MakeStricterForm): String =
        "Make focus mode stricter?\n\n" + stricterChanges(form).joinToString("\n") { "• $it" } +
            "\n\nYou can't undo this until focus mode ends."

    /** Why `tighten` rejected the changes; normally unreachable from the dialog. */
    fun tightenRejected(error: FocusError): String = when (error) {
        is FocusError.InvalidTransition ->
            if (error.from == FocusState.Unlocked) "Focus mode is off, so there is nothing to make stricter." else "This can't be changed now."
        is FocusError.NotStricter -> "Not applied: this would loosen " + error.loosenings.joinToString(", ") { loosening ->
            when (loosening) {
                is Loosening.ExitDelayShortened -> "the exit delay"
                is Loosening.AppRuleLoosened -> loosening.packageName
            }
        } + "."
        is FocusError.UnsupportedRules -> "Not applied: " + error.rules.joinToString(", ") {
            "${it.packageName} can't be set to ${SetupLabels.rule(it.rule)}"
        } + "."
    }

    const val STRICTER_FAILED = "Could not apply the changes."
}
