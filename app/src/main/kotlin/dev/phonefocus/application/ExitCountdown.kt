package dev.phonefocus.application

import dev.phonefocus.domain.FocusState
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.Locale

/**
 * A running exit countdown at one moment: the time left until the unlock ([remaining]) out of
 * the countdown's whole length ([total]).
 */
data class ExitCountdownStatus(val remaining: Duration, val total: Duration) {
    /**
     * How much of the countdown has passed, 0..1, for the progress bar. Derived from [remaining],
     * so the bar is full exactly when the countdown shows zero and `tick` unlocks.
     */
    val elapsedFraction: Float
        get() {
            if (total.isZero || total.isNegative) return 1f
            val elapsed = total - remaining
            return (elapsed.toMillis().toDouble() / total.toMillis()).coerceIn(0.0, 1.0).toFloat()
        }
}

/**
 * The time left on a running exit countdown, measured with the same trusted time
 * ([TrustedTime]) that [FocusCommands.tick] uses to unlock, so what the launcher shows matches
 * the actual unlock and changing the system time does not change it.
 *
 * The formatted texts round up, so they never show less time than is actually left (never
 * imply an earlier unlock).
 */
class ExitCountdown(
    private val repository: FocusSessionRepository,
    private val clock: DeviceClock,
) {
    /** The time left now, or null when no countdown runs. */
    suspend fun remaining(): Duration? = remaining(repository.records.first(), clock.read())

    /** The countdown now (time left and length), or null when none runs. */
    suspend fun status(): ExitCountdownStatus? = status(repository.records.first(), clock.read())

    companion object {
        /**
         * The time left on [record]'s countdown at [reading]: from the trusted time to `unlockAt`,
         * never negative. Null when the session is not [FocusState.ExitPending].
         */
        fun remaining(record: FocusRecord, reading: ClockReading): Duration? {
            val state = record.session.state as? FocusState.ExitPending ?: return null
            val now = TrustedTime.now(reading, record.checkpoint)
            val left = Duration.between(now, state.unlockAt)
            return if (left.isNegative) Duration.ZERO else left
        }

        /**
         * [record]'s countdown at [reading]: [remaining] out of its length, from
         * [FocusRecord.countdownStartedAt] to `unlockAt`. For a countdown without a known start
         * (started by an older version) the session's exit delay is the length, but never less
         * than the time left. Null when no countdown runs.
         */
        fun status(record: FocusRecord, reading: ClockReading): ExitCountdownStatus? {
            val state = record.session.state as? FocusState.ExitPending ?: return null
            val left = remaining(record, reading) ?: return null
            val known = record.countdownStartedAt
                ?.let { Duration.between(it, state.unlockAt) }
                ?.takeIf { !it.isNegative && !it.isZero }
            val total = known ?: record.session.exitDelay.duration
            return ExitCountdownStatus(left, maxOf(total, left))
        }

        /**
         * [remaining] as hours and minutes, e.g. "23:42" for 23 h 41 min 7 s or "168:00" for a
         * week. Rounded up to the next minute, so it never shows less than is left: "0:01" in the
         * last minute and "0:00" only once the countdown has ended.
         */
        fun format(remaining: Duration): String {
            val minutes = ceilDiv(wholeSecondsUp(remaining), 60)
            return String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60)
        }

        /**
         * [remaining] as hh:mm:ss for the exit-pending dialog, e.g. "23:41:07" or "167:59:59".
         * Rounded up to the next second, so "00:00:00" shows only once the countdown has ended.
         */
        fun formatWithSeconds(remaining: Duration): String {
            val seconds = wholeSecondsUp(remaining)
            return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        }

        private fun wholeSecondsUp(d: Duration): Long =
            if (d.isNegative) 0 else d.seconds + if (d.nano > 0) 1 else 0

        private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b
    }
}
