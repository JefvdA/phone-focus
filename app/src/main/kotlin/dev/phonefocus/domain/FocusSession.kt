package dev.phonefocus.domain

import java.time.Instant

/**
 * Aggregate root: the single source of truth for focus state (see #1).
 *
 * Immutable: every command returns a new session (wrapped in [FocusResult] when it can be
 * rejected). [policy] and [exitDelay] are enforced only while [state] is focused; while
 * [FocusState.Unlocked] they hold the last used (or initial) values.
 *
 * Invariants:
 * 1. While focused, [policy] and [exitDelay] can only get stricter ([tighten]).
 * 2. A running countdown keeps its original `unlockAt`; a longer delay applies from the next
 *    [requestExit].
 * 3. Only the countdown ending ([tick]) leaves focus.
 * 4. Once unlocked, the session stays [FocusState.Unlocked] until [startFocus].
 */
class FocusSession private constructor(
    val state: FocusState,
    val policy: AppPolicy,
    val exitDelay: ExitDelay,
) {
    /** Unlocked -> Focused, enforcing [policy] with [delay] as exit delay. */
    fun startFocus(policy: AppPolicy, delay: ExitDelay): FocusResult =
        if (state == FocusState.Unlocked) {
            success(FocusState.Focused, policy, delay)
        } else {
            invalid(FocusCommand.StartFocus)
        }

    /** Focused -> ExitPending, with the countdown ending at [now] + [exitDelay]. */
    fun requestExit(now: Instant): FocusResult =
        if (state == FocusState.Focused) {
            success(FocusState.ExitPending(now.plus(exitDelay.duration)))
        } else {
            invalid(FocusCommand.RequestExit)
        }

    fun requestExit(clock: Clock): FocusResult = requestExit(clock.now())

    /** ExitPending -> Focused. The countdown is discarded. */
    fun cancelExit(): FocusResult =
        if (state is FocusState.ExitPending) {
            success(FocusState.Focused)
        } else {
            invalid(FocusCommand.CancelExit)
        }

    /**
     * Advances time: ExitPending -> Unlocked once [now] has reached `unlockAt`. In every other
     * case the session is returned unchanged, so it is safe to call periodically.
     */
    fun tick(now: Instant): FocusSession {
        val current = state
        return if (current is FocusState.ExitPending && !now.isBefore(current.unlockAt)) {
            FocusSession(FocusState.Unlocked, policy, exitDelay)
        } else {
            this
        }
    }

    fun tick(clock: Clock): FocusSession = tick(clock.now())

    /**
     * Makes the policy and/or exit delay stricter while focused (Focused or ExitPending; the
     * state, including a running `unlockAt`, is kept). Rejected as a whole if any change would
     * loosen something; equal values are allowed and change nothing.
     */
    fun tighten(changes: Tightening): FocusResult {
        if (!state.isFocusActive) return invalid(FocusCommand.Tighten)

        val loosenings = buildList<Loosening> {
            val newDelay = changes.exitDelay
            if (newDelay != null && newDelay < exitDelay) {
                add(Loosening.ExitDelayShortened(exitDelay, newDelay))
            }
            addAll(policy.looseningsIn(changes.rules))
        }
        if (loosenings.isNotEmpty()) return FocusResult.Failure(FocusError.NotStricter(loosenings))

        return success(state, policy.with(changes.rules), changes.exitDelay ?: exitDelay)
    }

    /** Whether [packageName] may be launched now. Everything may launch while Unlocked. */
    fun isLaunchAllowed(packageName: String): Boolean =
        !state.isFocusActive || policy.ruleFor(packageName).isLaunchable

    /**
     * Whether the short-video feed of [packageName] must be blocked now. Never while Unlocked;
     * while focused for [AppRule.ShortVideoBlocked] and (stricter still) [AppRule.Blocked] apps.
     */
    fun isShortVideoBlocked(packageName: String): Boolean =
        state.isFocusActive && policy.ruleFor(packageName) != AppRule.Allowed

    /**
     * The packages the focus home screen offers: those the active policy lets launch. Empty
     * while Unlocked, because no policy is active then (use [isLaunchAllowed] for single apps).
     */
    fun allowedApps(): Set<String> =
        if (state.isFocusActive) policy.launchablePackages() else emptySet()

    private fun success(
        newState: FocusState,
        newPolicy: AppPolicy = policy,
        newDelay: ExitDelay = exitDelay,
    ): FocusResult = FocusResult.Success(FocusSession(newState, newPolicy, newDelay))

    private fun invalid(command: FocusCommand): FocusResult =
        FocusResult.Failure(FocusError.InvalidTransition(command, state))

    override fun equals(other: Any?): Boolean =
        other is FocusSession &&
            other.state == state &&
            other.policy == policy &&
            other.exitDelay == exitDelay

    override fun hashCode(): Int = 31 * (31 * state.hashCode() + policy.hashCode()) + exitDelay.hashCode()

    override fun toString(): String = "FocusSession(state=$state, policy=$policy, exitDelay=$exitDelay)"

    companion object {
        /** A fresh, unlocked session with an empty policy and a one-day exit delay as defaults. */
        fun initial(
            policy: AppPolicy = AppPolicy.EMPTY,
            exitDelay: ExitDelay = ExitDelay.ONE_DAY,
        ): FocusSession = FocusSession(FocusState.Unlocked, policy, exitDelay)

        /**
         * Rehydrates a session that was persisted earlier (state, policy and delay exactly as
         * saved). Only for repositories loading a previously saved session; not a command, so
         * it must never be used to bypass the transitions above.
         */
        fun restore(state: FocusState, policy: AppPolicy, exitDelay: ExitDelay): FocusSession =
            FocusSession(state, policy, exitDelay)
    }
}
