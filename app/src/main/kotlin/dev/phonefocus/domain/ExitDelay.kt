package dev.phonefocus.domain

import java.time.Duration

/**
 * Value object: how long the countdown runs between `requestExit` and unlocking.
 *
 * Must be positive. While focused it may only get longer (invariant 1 in #1).
 */
@JvmInline
value class ExitDelay(val duration: Duration) : Comparable<ExitDelay> {
    init {
        require(!duration.isNegative && !duration.isZero) { "ExitDelay must be positive, was $duration" }
    }

    override fun compareTo(other: ExitDelay): Int = duration.compareTo(other.duration)

    companion object {
        val TWELVE_HOURS: ExitDelay = ExitDelay(Duration.ofHours(12))
        val ONE_DAY: ExitDelay = ExitDelay(Duration.ofHours(24))
        val TWO_DAYS: ExitDelay = ExitDelay(Duration.ofHours(48))
        val SEVEN_DAYS: ExitDelay = ExitDelay(Duration.ofDays(7))

        /** The presets offered to the user, shortest first. */
        val PRESETS: List<ExitDelay> = listOf(TWELVE_HOURS, ONE_DAY, TWO_DAYS, SEVEN_DAYS)
    }
}
