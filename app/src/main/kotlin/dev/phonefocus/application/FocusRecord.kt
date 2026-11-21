package dev.phonefocus.application

import dev.phonefocus.domain.FocusSession
import java.time.Duration
import java.time.Instant

/**
 * What is persisted: the [session] plus, while an exit countdown runs, the [checkpoint] the
 * countdown is measured from (see [TrustedTime]).
 */
data class FocusRecord(
    val session: FocusSession,
    val checkpoint: TimeCheckpoint? = null,
) {
    companion object {
        /** Nothing persisted yet: a fresh, unlocked session. */
        val INITIAL: FocusRecord = FocusRecord(FocusSession.initial())
    }
}

/**
 * The last time the app trusted: at [elapsedRealtime] since boot number [bootCount], the
 * trusted time was [trustedTime].
 */
data class TimeCheckpoint(
    val trustedTime: Instant,
    val elapsedRealtime: Duration,
    val bootCount: Int,
)

/**
 * One reading of the device clocks: the wall clock ([wallTime], user-changeable), the
 * monotonic time since boot ([elapsedRealtime], not changeable, resets on reboot) and the
 * boot number ([bootCount], increases on every reboot).
 */
data class ClockReading(
    val wallTime: Instant,
    val elapsedRealtime: Duration,
    val bootCount: Int,
)
