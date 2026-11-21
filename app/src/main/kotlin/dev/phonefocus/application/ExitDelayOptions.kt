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
}
