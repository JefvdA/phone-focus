package dev.phonefocus.application

import dev.phonefocus.domain.AppAccess
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.FocusState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import java.time.Duration

/** A quick button of the cover screen (#13). Configurable later; [DEFAULTS] for now. */
enum class QuickAction(val label: String) {
    Call("Call"),
    Music("Music"),
    Camera("Camera"),
    Timer("Timer"),
    ;

    companion object {
        /** The 2x2 grid, row by row: Call, Music / Camera, Timer. */
        val DEFAULTS: List<QuickAction> = listOf(Call, Music, Camera, Timer)
    }
}

/**
 * Which app handles a [QuickAction] on this device (the default dialer, music player, camera
 * and clock). Implemented in `infrastructure`.
 */
fun interface QuickActionResolver {
    /** The package that opens [action], or null when no single app handles it. */
    fun packageFor(action: QuickAction): String?
}

/** The focus status the cover screen shows. */
sealed interface CoverStatus {
    val text: String

    data object Unlocked : CoverStatus {
        override val text = "Unlocked"
    }

    data object Focus : CoverStatus {
        override val text = "Focus"
    }

    /**
     * The exit countdown runs, [remaining] left. The text rounds up to the minute
     * ([ExitCountdown.format]), so it never shows less time than is actually left.
     */
    data class ExitPending(val remaining: Duration) : CoverStatus {
        override val text: String get() = "Exit in ${ExitCountdown.format(remaining)}"
    }
}

/** A quick button and whether it can be used now. */
data class QuickButton(val action: QuickAction, val packageName: String?, val enabled: Boolean)

/** What the cover-screen widget and the cover activity show (#13). */
data class CoverScreen(val status: CoverStatus, val buttons: List<QuickButton>) {
    /**
     * How long until [status]'s text changes (the shown minute of the countdown goes down), so
     * the widget can be refreshed exactly then and no more often. Null when the text cannot
     * change by itself (no countdown, or it has ended: the unlock then refreshes the widget).
     */
    val nextRefreshIn: Duration? get() = (status as? CoverStatus.ExitPending)?.let { nextMinuteChange(it.remaining) }

    companion object {
        /**
         * The cover screen for [record] at [reading]: the status, with the countdown measured
         * like the actual unlock ([ExitCountdown]), and [actions] as buttons. While focused a
         * button is enabled only when [AppAccess] lets its app launch (the default dialer is
         * essential, so Call stays usable); a button whose action no app handles is disabled.
         */
        fun of(
            record: FocusRecord,
            reading: ClockReading,
            essentialApps: EssentialApps,
            resolver: QuickActionResolver,
            actions: List<QuickAction> = QuickAction.DEFAULTS,
        ): CoverScreen {
            val state = record.session.state
            val status = when (state) {
                FocusState.Unlocked -> CoverStatus.Unlocked
                FocusState.Focused -> CoverStatus.Focus
                is FocusState.ExitPending -> CoverStatus.ExitPending(ExitCountdown.remaining(record, reading) ?: Duration.ZERO)
            }
            val access = AppAccess(record.session, essentialApps)
            val buttons = actions.map { action ->
                val packageName = resolver.packageFor(action)
                QuickButton(action, packageName, packageName != null && access.canLaunch(packageName))
            }
            return CoverScreen(status, buttons)
        }

        /**
         * The time until [ExitCountdown.format] of [remaining] shows one minute less: it shows
         * m = ceil(remaining) minutes until remaining reaches (m - 1) minutes. Null once nothing
         * is left.
         */
        fun nextMinuteChange(remaining: Duration): Duration? {
            if (remaining.isNegative || remaining.isZero) return null
            val shownMinutes = (remaining.seconds + (if (remaining.nano > 0) 1 else 0) + 59) / 60
            return remaining - Duration.ofMinutes(shownMinutes - 1)
        }
    }
}

/**
 * The [CoverScreen] now ([current]) or as a flow that updates when the session or the
 * essential apps change and when the shown countdown minute changes ([invoke]).
 */
class ObserveCoverScreen(
    private val repository: FocusSessionRepository,
    private val clock: DeviceClock,
    private val essentialApps: EssentialAppsSource,
    private val resolver: QuickActionResolver,
) {
    suspend fun current(): CoverScreen =
        CoverScreen.of(repository.records.first(), clock.read(), essentialApps.essentialApps.first(), resolver)

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<CoverScreen> =
        combine(repository.records, essentialApps.essentialApps) { record, essential -> record to essential }
            .transformLatest { (record, essential) ->
                while (true) {
                    val screen = CoverScreen.of(record, clock.read(), essential, resolver)
                    emit(screen)
                    delay(screen.nextRefreshIn?.toMillis() ?: break)
                }
            }
            .distinctUntilChanged()
}

/**
 * Whether a quick button may open its app right now, through [AppAccess], checked on every tap
 * so a stale widget can never open a blocked app. Returns the package to open (the one that was
 * checked), or null when it is blocked or no app handles the action.
 */
class CheckQuickAction(
    private val sessions: ObserveFocusSession,
    private val essentialApps: EssentialAppsSource,
    private val resolver: QuickActionResolver,
) {
    suspend operator fun invoke(action: QuickAction): String? {
        val packageName = resolver.packageFor(action) ?: return null
        val access = AppAccess(sessions.current(), essentialApps.essentialApps.first())
        return packageName.takeIf(access::canLaunch)
    }
}
