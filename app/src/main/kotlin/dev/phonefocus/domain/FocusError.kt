package dev.phonefocus.domain

/** The commands a [FocusSession] accepts that can be rejected. */
enum class FocusCommand { StartFocus, RequestExit, CancelExit, Tighten }

/** Why a [FocusSession] command was rejected. Rule violations are values, never exceptions. */
sealed interface FocusError {
    /** [command] is not allowed in state [from] (see the transition table in #1). */
    data class InvalidTransition(val command: FocusCommand, val from: FocusState) : FocusError

    /** A `tighten` contained changes that would loosen the policy or delay; nothing was applied. */
    data class NotStricter(val loosenings: List<Loosening>) : FocusError
}

/** A single change that would make the policy or delay less strict. */
sealed interface Loosening {
    data class ExitDelayShortened(val current: ExitDelay, val requested: ExitDelay) : Loosening

    data class AppRuleLoosened(
        val packageName: String,
        val current: AppRule,
        val requested: AppRule,
    ) : Loosening
}

/** The outcome of a [FocusSession] command: the new session, or the reason it was rejected. */
sealed interface FocusResult {
    data class Success(val session: FocusSession) : FocusResult

    data class Failure(val error: FocusError) : FocusResult
}
