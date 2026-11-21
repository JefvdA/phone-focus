package dev.phonefocus.application

import dev.phonefocus.domain.FocusState
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.Locale

/**
 * The time left on a running exit countdown, measured with the same trusted time
 * ([TrustedTime]) that [FocusCommands.tick] uses to unlock, so what the launcher shows matches
 * the actual unlock and changing the system time does not change it.
 */
class ExitCountdown(
    private val repository: FocusSessionRepository,
    private val clock: DeviceClock,
) {
    /** The time left now, or null when no countdown runs. */
    suspend fun remaining(): Duration? = remaining(repository.records.first(), clock.read())

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
         * [remaining] as hours and minutes, e.g. "23:41" for 23 h 41 min 7 s or "167:59" for almost
         * a week. Seconds are cut off, like a countdown clock, so it reads "0:00" in the last minute.
         */
        fun format(remaining: Duration): String {
            val minutes = if (remaining.isNegative) 0 else remaining.toMinutes()
            return String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60)
        }
    }
}
