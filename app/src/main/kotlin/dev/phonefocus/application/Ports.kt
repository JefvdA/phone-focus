package dev.phonefocus.application

import kotlinx.coroutines.flow.Flow
import java.time.Duration

/** Persistence of the [FocusRecord]. Implemented in `infrastructure`. */
interface FocusSessionRepository {
    /** The persisted record, [FocusRecord.INITIAL] when nothing is stored. Emits on every change. */
    val records: Flow<FocusRecord>

    /**
     * Atomically replaces the record with [transform] of the current one and returns the new
     * record. Concurrent updates are applied one after another, never interleaved.
     */
    suspend fun update(transform: (FocusRecord) -> FocusRecord): FocusRecord
}

/** Reads the device clocks. Implemented in `infrastructure`. */
fun interface DeviceClock {
    fun read(): ClockReading
}

/**
 * Wakes the app so it can run [FocusCommands.tick] when the countdown should end. Implemented in
 * `infrastructure` with an alarm on the monotonic clock, so wall-clock changes don't move it.
 */
interface UnlockScheduler {
    /** Replaces any scheduled unlock with one [delay] from now (monotonic time). */
    fun scheduleUnlockIn(delay: Duration)

    /** Cancels the scheduled unlock, if any. */
    fun cancel()
}
