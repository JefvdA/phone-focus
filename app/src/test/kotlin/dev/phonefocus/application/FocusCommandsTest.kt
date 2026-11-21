package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusError
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import dev.phonefocus.domain.Tightening
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FocusCommandsTest {
    private val t0 = Instant.parse("2026-01-01T08:00:00Z")
    private val policy = AppPolicy(mapOf("org.phone" to AppRule.Allowed, "com.instagram" to AppRule.ShortVideoBlocked))
    private val delay = ExitDelay.ONE_DAY
    private val day = Duration.ofDays(1)

    private val repo = FakeRepository()
    private val clock = FakeDeviceClock(t0)
    private val scheduler = FakeScheduler()
    private val commands = FocusCommands(repo, clock, scheduler)
    private val observe = ObserveFocusSession(repo)

    private val record get() = repo.state.value
    private val state get() = record.session.state

    private fun FocusResult.assertSuccess() = assertTrue("expected success, got $this", this is FocusResult.Success)

    private fun focused() = runBlocking { commands.startFocus(policy, delay).assertSuccess() }
    private fun exitPending() = runBlocking {
        focused()
        commands.requestExit().assertSuccess()
    }

    // --- persistence of commands -----------------------------------------------------------

    @Test
    fun `startFocus persists the focused session and schedules nothing`() = runBlocking {
        commands.startFocus(policy, delay).assertSuccess()
        assertEquals(FocusState.Focused, state)
        assertEquals(policy, record.session.policy)
        assertNull(record.checkpoint)
        assertNull(scheduler.scheduled)
    }

    @Test
    fun `state survives a new process (new use cases on the same storage)`() = runBlocking {
        exitPending()
        val restarted = ObserveFocusSession(repo)
        assertEquals(FocusState.ExitPending(t0 + day), restarted.current().state)
    }

    @Test
    fun `observe emits the persisted session`() = runBlocking {
        assertEquals(FocusSession.initial(), observe().first())
        focused()
        assertEquals(FocusState.Focused, observe().first().state)
    }

    @Test
    fun `requestExit sets unlockAt to now plus delay, stores a checkpoint and schedules the unlock`() = runBlocking {
        focused()
        commands.requestExit().assertSuccess()
        assertEquals(FocusState.ExitPending(t0 + day), state)
        assertEquals(TimeCheckpoint(t0, clock.elapsedRealtime, clock.bootCount), record.checkpoint)
        assertEquals(day, scheduler.scheduled)
    }

    @Test
    fun `cancelExit returns to Focused, clears the checkpoint and cancels the unlock`() = runBlocking {
        exitPending()
        commands.cancelExit().assertSuccess()
        assertEquals(FocusState.Focused, state)
        assertNull(record.checkpoint)
        assertNull(scheduler.scheduled)
    }

    @Test
    fun `tighten keeps a running countdown and its unlock`() = runBlocking {
        exitPending()
        clock.advance(Duration.ofHours(2))
        commands.tighten(Tightening(mapOf("org.phone" to AppRule.Blocked), ExitDelay.SEVEN_DAYS)).assertSuccess()
        assertEquals(FocusState.ExitPending(t0 + day), state)
        assertEquals(ExitDelay.SEVEN_DAYS, record.session.exitDelay)
        assertEquals(Duration.ofHours(22), scheduler.scheduled)
    }

    @Test
    fun `rejected commands change nothing`() = runBlocking {
        exitPending()
        val before = record
        val writes = repo.writes
        val calls = scheduler.calls.size
        val loosen = commands.tighten(Tightening(exitDelay = ExitDelay.TWELVE_HOURS))
        val start = commands.startFocus(AppPolicy.EMPTY, delay)
        assertTrue((loosen as FocusResult.Failure).error is FocusError.NotStricter)
        assertTrue((start as FocusResult.Failure).error is FocusError.InvalidTransition)
        assertEquals(before, record)
        assertEquals(writes, repo.writes)
        assertEquals(calls, scheduler.calls.size)
    }

    // --- tick ------------------------------------------------------------------------------

    @Test
    fun `tick before unlockAt keeps the countdown and re-schedules the remaining time`() = runBlocking {
        exitPending()
        clock.advance(Duration.ofHours(23))
        assertEquals(FocusState.ExitPending(t0 + day), commands.tick().state)
        assertEquals(Duration.ofHours(1), scheduler.scheduled)
        assertEquals(t0 + Duration.ofHours(23), record.checkpoint?.trustedTime)
    }

    @Test
    fun `tick at unlockAt unlocks, clears the checkpoint and cancels the unlock`() = runBlocking {
        exitPending()
        clock.advance(day)
        assertEquals(FocusState.Unlocked, commands.tick().state)
        assertEquals(FocusState.Unlocked, state)
        assertNull(record.checkpoint)
        assertNull(scheduler.scheduled)
    }

    @Test
    fun `tick while Focused or Unlocked changes nothing`() = runBlocking {
        assertEquals(FocusState.Unlocked, commands.tick().state)
        focused()
        clock.advance(Duration.ofDays(400))
        assertEquals(FocusState.Focused, commands.tick().state)
        assertNull(record.checkpoint)
    }

    @Test
    fun `focus survives a reboot`() = runBlocking {
        focused()
        clock.reboot(off = Duration.ofMinutes(1))
        assertEquals(FocusState.Focused, commands.tick().state)
    }

    // --- clock tampering -------------------------------------------------------------------

    @Test
    fun `setting the clock forward does not end the countdown early`() = runBlocking {
        exitPending()
        clock.advance(Duration.ofMinutes(10))
        clock.changeSystemTime(t0 + Duration.ofDays(30))
        assertEquals(FocusState.ExitPending(t0 + day), commands.tick().state)
        assertEquals(day - Duration.ofMinutes(10), scheduler.scheduled)
    }

    @Test
    fun `setting the clock back does not delay the countdown`() = runBlocking {
        exitPending()
        clock.changeSystemTime(t0 - Duration.ofDays(30))
        clock.advance(day)
        assertEquals(FocusState.Unlocked, commands.tick().state)
    }

    @Test
    fun `setting the clock forward before requesting exit still runs the full delay`() = runBlocking {
        focused()
        clock.changeSystemTime(t0 + Duration.ofDays(30))
        commands.requestExit().assertSuccess()
        clock.changeSystemTime(t0)
        clock.advance(day - Duration.ofSeconds(1))
        assertTrue(commands.tick().state is FocusState.ExitPending)
        clock.advance(Duration.ofSeconds(1))
        assertEquals(FocusState.Unlocked, commands.tick().state)
    }

    @Test
    fun `reboot mid-countdown with the clock set forward does not unlock early`() = runBlocking {
        exitPending()
        clock.advance(Duration.ofHours(1))
        commands.tick() // checkpoint at t0 + 1h
        clock.reboot(off = Duration.ofMinutes(5))
        clock.changeSystemTime(t0 + Duration.ofDays(30))
        assertTrue(commands.tick().state is FocusState.ExitPending)
        // Trusted time continues from the checkpoint plus time since boot (30s).
        assertEquals(Duration.ofHours(23) - Duration.ofSeconds(30), scheduler.scheduled)
    }

    @Test
    fun `reboot mid-countdown unlocks once the remaining time has passed, never earlier`() = runBlocking {
        val oneMinute = ExitDelayOptions.DEBUG_ONE_MINUTE
        commands.startFocus(policy, oneMinute).assertSuccess()
        commands.requestExit().assertSuccess()
        clock.advance(Duration.ofSeconds(20))
        commands.tick() // e.g. ACTION_SHUTDOWN
        clock.reboot(off = Duration.ofSeconds(30), upFor = Duration.ofSeconds(10))
        assertTrue(commands.tick().state is FocusState.ExitPending) // boot
        assertEquals(Duration.ofSeconds(30), scheduler.scheduled)
        clock.advance(Duration.ofSeconds(29))
        assertTrue(commands.tick().state is FocusState.ExitPending)
        clock.advance(Duration.ofSeconds(1))
        assertEquals(FocusState.Unlocked, commands.tick().state) // the alarm fires
    }

    @Test
    fun `several reboots keep accumulating from the latest checkpoint`() = runBlocking {
        exitPending()
        repeat(3) {
            clock.advance(Duration.ofHours(4))
            commands.tick()
            clock.reboot(off = Duration.ofHours(1), upFor = Duration.ZERO)
        }
        assertNotNull(record.checkpoint)
        commands.tick()
        assertEquals(t0 + Duration.ofHours(12), record.checkpoint?.trustedTime)
        assertEquals(Duration.ofHours(12), scheduler.scheduled)
    }
}
