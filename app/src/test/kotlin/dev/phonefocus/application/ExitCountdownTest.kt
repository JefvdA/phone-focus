package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ExitCountdownTest {
    private val start = Instant.parse("2026-03-01T10:00:00Z")
    private val clock = FakeDeviceClock(start)
    private val repository = FakeRepository()
    private val commands = FocusCommands(repository, clock, FakeScheduler())
    private val countdown = ExitCountdown(repository, clock)

    private suspend fun startCountdown() {
        assertTrue(commands.startFocus(AppPolicy.EMPTY, ExitDelay.ONE_DAY) is FocusResult.Success)
        assertTrue(commands.requestExit() is FocusResult.Success)
    }

    @Test
    fun `no countdown while unlocked or focused`() = runBlocking {
        assertNull(countdown.remaining())
        commands.startFocus(AppPolicy.EMPTY, ExitDelay.ONE_DAY)
        assertNull(countdown.remaining())
    }

    @Test
    fun `remaining time counts down from the exit delay`() = runBlocking {
        startCountdown()
        assertEquals(Duration.ofHours(24), countdown.remaining())
        clock.advance(Duration.ofMinutes(18).plusSeconds(53))
        assertEquals(Duration.ofHours(23).plusMinutes(41).plusSeconds(7), countdown.remaining())
    }

    @Test
    fun `changing the system time does not change the remaining time`() = runBlocking {
        startCountdown()
        clock.advance(Duration.ofHours(1))
        clock.changeSystemTime(start.plus(Duration.ofDays(30)))
        assertEquals(Duration.ofHours(23), countdown.remaining())
        clock.changeSystemTime(start.minus(Duration.ofDays(30)))
        assertEquals(Duration.ofHours(23), countdown.remaining())
    }

    @Test
    fun `time the phone was off is not counted, as for the actual unlock`() = runBlocking {
        startCountdown()
        clock.advance(Duration.ofHours(2))
        commands.tick() // checkpoint at 2 h
        clock.reboot(off = Duration.ofHours(5), upFor = Duration.ofMinutes(10))
        assertEquals(Duration.ofHours(21).plusMinutes(50), countdown.remaining())
    }

    @Test
    fun `remaining time reaches zero exactly when tick unlocks`() = runBlocking {
        startCountdown()
        clock.advance(Duration.ofHours(24).minusSeconds(1))
        assertEquals(Duration.ofSeconds(1), countdown.remaining())
        assertTrue(commands.tick().state is FocusState.ExitPending)

        clock.advance(Duration.ofSeconds(1))
        assertEquals(Duration.ZERO, countdown.remaining())
        assertEquals(FocusState.Unlocked, commands.tick().state)
        assertNull(countdown.remaining())
    }

    @Test
    fun `remaining time is never negative`() = runBlocking {
        startCountdown()
        clock.advance(Duration.ofDays(3))
        assertEquals(Duration.ZERO, countdown.remaining())
    }

    @Test
    fun `format shows hours and minutes, seconds cut off`() {
        assertEquals("23:41", ExitCountdown.format(Duration.ofHours(23).plusMinutes(41).plusSeconds(7)))
        assertEquals("24:00", ExitCountdown.format(Duration.ofHours(24)))
        assertEquals("167:59", ExitCountdown.format(Duration.ofDays(7).minusSeconds(1)))
        assertEquals("1:05", ExitCountdown.format(Duration.ofMinutes(65)))
        assertEquals("0:00", ExitCountdown.format(Duration.ofSeconds(59)))
        assertEquals("0:00", ExitCountdown.format(Duration.ZERO))
        assertEquals("0:00", ExitCountdown.format(Duration.ofSeconds(-5)))
    }
}
