package dev.phonefocus.application

import java.time.Duration
import java.time.Instant

/**
 * Clock-tampering guard for the exit countdown (decision in #4).
 *
 * While no countdown runs there is no checkpoint and the wall clock is used as is. Once a
 * countdown starts, time is measured from a [TimeCheckpoint] with the monotonic clock:
 *
 * - Same boot: trusted time = checkpoint + monotonic time elapsed since the checkpoint, so
 *   changing the system time has no effect.
 * - After a reboot (the monotonic clock restarted): trusted time = checkpoint + monotonic time
 *   since boot. The time the phone was off is not counted, so the countdown can only end
 *   later, never earlier.
 *
 * Residual risk: the countdown pauses while the phone is off, so a shown `unlockAt` can be
 * off by that time. Time settings stay changeable until Device Owner (#14) locks them.
 */
object TrustedTime {
    /** The current trusted time for [reading], measured from [checkpoint] when there is one. */
    fun now(reading: ClockReading, checkpoint: TimeCheckpoint?): Instant {
        if (checkpoint == null) return reading.wallTime
        val sameBoot = reading.bootCount == checkpoint.bootCount &&
            reading.elapsedRealtime >= checkpoint.elapsedRealtime
        val elapsedSinceCheckpoint: Duration =
            if (sameBoot) reading.elapsedRealtime - checkpoint.elapsedRealtime else reading.elapsedRealtime
        return checkpoint.trustedTime + elapsedSinceCheckpoint.coerceAtLeast(Duration.ZERO)
    }

    /** A checkpoint saying that at [reading] the trusted time was [trustedTime]. */
    fun checkpoint(reading: ClockReading, trustedTime: Instant): TimeCheckpoint =
        TimeCheckpoint(trustedTime, reading.elapsedRealtime, reading.bootCount)

    private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this
}
