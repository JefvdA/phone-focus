package dev.phonefocus.domain

import java.time.Instant

/** The state of a [FocusSession]. */
sealed interface FocusState {
    /** Normal phone; no policy is enforced. */
    data object Unlocked : FocusState

    /** Focus mode, indefinitely. Only allowed apps are usable. */
    data object Focused : FocusState

    /** Still focused; the exit countdown is running and ends at [unlockAt]. */
    data class ExitPending(val unlockAt: Instant) : FocusState

    /** True while the policy is enforced ([Focused] or [ExitPending]). */
    val isFocusActive: Boolean get() = this != Unlocked
}
