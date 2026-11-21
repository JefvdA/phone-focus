package dev.phonefocus.application

import dev.phonefocus.domain.ExitDelay
import java.time.Duration

/** The exit delays the app offers. */
object ExitDelayOptions {
    /**
     * A one-minute delay for testing the countdown (reboot tests in #4, the C5 flow in #10).
     * Only offered in debug builds.
     */
    val DEBUG_ONE_MINUTE: ExitDelay = ExitDelay(Duration.ofMinutes(1))

    /** The delays to offer: the presets, plus [DEBUG_ONE_MINUTE] first when [debugBuild]. */
    fun available(debugBuild: Boolean): List<ExitDelay> =
        if (debugBuild) listOf(DEBUG_ONE_MINUTE) + ExitDelay.PRESETS else ExitDelay.PRESETS

    /**
     * The delays to show when making focus stricter (#9) with [current] as the exit delay: every
     * delay in [available] plus [current] (should it not be offered anymore), shortest first.
     * Shorter delays than [current] are shown but disabled; [current] itself can be chosen and
     * changes nothing. The domain still rejects a shorter delay ([dev.phonefocus.domain.FocusSession.tighten]).
     */
    fun forTightening(current: ExitDelay, available: List<ExitDelay>): List<ExitDelayChoice> =
        (available + current).distinct().sorted().map { ExitDelayChoice(it, enabled = it >= current) }
}

/** An exit delay option on screen, which can only be chosen when [enabled]. */
data class ExitDelayChoice(val delay: ExitDelay, val enabled: Boolean)
