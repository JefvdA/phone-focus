package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import dev.phonefocus.domain.Tightening
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/** The persisted focus session, for UI, accessibility service and widget. */
class ObserveFocusSession(private val repository: FocusSessionRepository) {
    operator fun invoke(): Flow<FocusSession> =
        repository.records.map { it.session }.distinctUntilChanged()

    suspend fun current(): FocusSession = invoke().first()
}

/**
 * Runs domain commands against the persisted session: loads the record, applies the command
 * atomically with the current trusted time ([TrustedTime]), persists the result and keeps the
 * scheduled unlock in sync. A rejected command changes nothing.
 *
 * Whole commands (clock reading, update and alarm sync) run one at a time under [mutex], so a
 * reading is never older than the checkpoint it is compared with, and a slower command cannot
 * cancel or replace the alarm a later one scheduled. Use one instance per process.
 *
 * When a [tick] ends the countdown (ExitPending -> Unlocked), [notifier] tells the user, once.
 */
class FocusCommands(
    private val repository: FocusSessionRepository,
    private val clock: DeviceClock,
    private val scheduler: UnlockScheduler,
    private val notifier: FocusNotifier = FocusNotifier.NONE,
) {
    private val mutex = Mutex()

    /** Unlocked -> Focused. */
    suspend fun startFocus(policy: AppPolicy, delay: ExitDelay): FocusResult =
        apply { session, _ -> session.startFocus(policy, delay) }

    /** Focused -> ExitPending; starts the countdown and schedules the unlock. */
    suspend fun requestExit(): FocusResult = apply { session, now -> session.requestExit(now) }

    /** ExitPending -> Focused; cancels the scheduled unlock. */
    suspend fun cancelExit(): FocusResult = apply { session, _ -> session.cancelExit() }

    /** Makes policy and/or delay stricter while focused. A running countdown is untouched. */
    suspend fun tighten(changes: Tightening): FocusResult = apply { session, _ -> session.tighten(changes) }

    /**
     * Advances time: unlocks once the countdown has ended, otherwise refreshes the time
     * checkpoint and re-schedules the unlock. Safe to call any time; run it on app start, boot,
     * screen-on, shutdown and when the unlock alarm fires. The tick that ends the countdown
     * posts the "Focus ended" notification ([FocusNotifier]); later ticks don't.
     */
    suspend fun tick(): FocusSession = mutex.withLock {
        var ended = false
        val record = repository.update { record ->
            val reading = clock.read()
            val now = TrustedTime.now(reading, record.checkpoint)
            val next = record.session.tick(now)
            ended = focusEnded(record.session, next)
            nextRecord(record, next, reading, now)
        }
        syncUnlockAlarm(record)
        if (ended) notifier.focusEnded()
        record.session
    }

    private suspend fun apply(command: (FocusSession, Instant) -> FocusResult): FocusResult = mutex.withLock {
        lateinit var result: FocusResult
        val record = repository.update { record ->
            val reading = clock.read()
            val now = TrustedTime.now(reading, record.checkpoint)
            result = command(record.session, now)
            when (val r = result) {
                is FocusResult.Failure -> record
                is FocusResult.Success -> nextRecord(record, r.session, reading, now)
            }
        }
        if (result is FocusResult.Success) syncUnlockAlarm(record)
        result
    }

    /**
     * [session] with a checkpoint at [now] while its countdown runs, without one otherwise. The
     * countdown start is [now] for a new countdown and kept for a running one (same `unlockAt`).
     */
    private fun nextRecord(previous: FocusRecord, session: FocusSession, reading: ClockReading, now: Instant): FocusRecord {
        if (session.state !is FocusState.ExitPending) return FocusRecord(session)
        val startedAt = if (previous.session.state == session.state) previous.countdownStartedAt else now
        return FocusRecord(session, TrustedTime.checkpoint(reading, now), startedAt)
    }

    /** Whether going from [before] to [after] is the countdown ending (ExitPending -> Unlocked). */
    private fun focusEnded(before: FocusSession, after: FocusSession): Boolean =
        before.state is FocusState.ExitPending && after.state == FocusState.Unlocked

    /** Schedules the unlock for the remaining countdown, or cancels it when none runs. */
    private fun syncUnlockAlarm(record: FocusRecord) {
        val state = record.session.state
        val checkpoint = record.checkpoint
        if (state is FocusState.ExitPending && checkpoint != null) {
            val remaining = Duration.between(checkpoint.trustedTime, state.unlockAt)
            scheduler.scheduleUnlockIn(if (remaining.isNegative) Duration.ZERO else remaining)
        } else {
            scheduler.cancel()
        }
    }
}
