package dev.phonefocus.application

import dev.phonefocus.domain.EssentialApps
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

/** The installed apps that have a launcher entry. Implemented in `infrastructure`. */
interface InstalledAppsSource {
    /** The current apps, re-emitted whenever an app is installed, removed, updated or changed. */
    val apps: Flow<List<InstalledApp>>
}

/**
 * Resolves the device's [EssentialApps] (this app, Settings, the default dialer). Implemented in
 * `infrastructure`.
 */
interface EssentialAppsSource {
    /** The current essential apps, re-emitted when they may have changed (e.g. a new default dialer). */
    val essentialApps: Flow<EssentialApps>
}
