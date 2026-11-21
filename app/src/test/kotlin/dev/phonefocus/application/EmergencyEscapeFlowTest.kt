package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The emergency escape flow of #10 with the debug one-minute delay, through [FocusCommands] and
 * [ExitCountdown] as the launcher uses them: request -> cancel -> request -> unlock.
 */
class EmergencyEscapeFlowTest {
    private val t0 = Instant.parse("2026-11-21T09:00:00Z")
    private val repo = FakeRepository()
    private val clock = FakeDeviceClock(t0)
    private val scheduler = FakeScheduler()
    private val notifier = FakeNotifier()
    private val commands = FocusCommands(repo, clock, scheduler, notifier)
    private val countdown = ExitCountdown(repo, clock)
    private val minute = Duration.ofMinutes(1)

    private val state get() = repo.state.value.session.state

    private fun FocusResult.assertSuccess() = assertTrue("expected success, got $this", this is FocusResult.Success)

    @Test
    fun `request, cancel, request, unlock with the debug one-minute delay`() = runBlocking {
        val policy = AppPolicy(mapOf("org.phone" to AppRule.Allowed))
        commands.startFocus(policy, ExitDelayOptions.DEBUG_ONE_MINUTE).assertSuccess()
        assertNull(countdown.status())

        // Request: the full minute is left, the bar is empty and the unlock is scheduled.
        commands.requestExit().assertSuccess()
        assertEquals(FocusState.ExitPending(t0 + minute), state)
        assertEquals(ExitCountdownStatus(minute, minute), countdown.status())
        assertEquals("00:01:00", ExitCountdown.formatWithSeconds(countdown.status()!!.remaining))
        assertEquals(minute, scheduler.scheduled)

        // 20 s later the user cancels: focused again, no countdown, no alarm.
        clock.advance(Duration.ofSeconds(20))
        assertEquals("00:00:40", ExitCountdown.formatWithSeconds(countdown.remaining()!!))
        commands.cancelExit().assertSuccess()
        assertEquals(FocusState.Focused, state)
        assertNull(countdown.status())
        assertNull(scheduler.scheduled)

        // Waiting does nothing while focused.
        clock.advance(Duration.ofMinutes(5))
        assertEquals(FocusState.Focused, commands.tick().state)

        // Request again: a fresh full minute, not what was left of the first one.
        commands.requestExit().assertSuccess()
        val unlockAt = clock.wallTime + minute
        assertEquals(FocusState.ExitPending(unlockAt), state)
        assertEquals(minute, countdown.remaining())
        assertEquals(minute, scheduler.scheduled)

        // Half way: the bar is half full and the shown time is what is actually left.
        clock.advance(Duration.ofSeconds(30))
        assertEquals(0.5f, countdown.status()!!.elapsedFraction, 1e-6f)
        assertEquals("00:00:30", ExitCountdown.formatWithSeconds(countdown.remaining()!!))
        assertEquals("0:01", ExitCountdown.format(countdown.remaining()!!))

        // One second before the end nothing unlocks, and nothing claims it has.
        clock.advance(Duration.ofSeconds(29).plusMillis(500))
        assertEquals("00:00:01", ExitCountdown.formatWithSeconds(countdown.remaining()!!))
        assertTrue(commands.tick().state is FocusState.ExitPending)
        assertEquals(0, notifier.focusEndedCount)

        // The alarm fires at the end: unlocked, alarm cleared, "Focus ended" posted once.
        clock.advance(Duration.ofMillis(500))
        assertEquals(1f, countdown.status()!!.elapsedFraction, 0f)
        assertEquals("00:00:00", ExitCountdown.formatWithSeconds(countdown.remaining()!!))
        assertEquals(FocusState.Unlocked, commands.tick().state)
        assertNull(scheduler.scheduled)
        assertNull(countdown.status())
        assertEquals(1, notifier.focusEndedCount)

        // Further ticks (screen-on, app start) don't notify again.
        commands.tick()
        assertEquals(1, notifier.focusEndedCount)
    }
}
