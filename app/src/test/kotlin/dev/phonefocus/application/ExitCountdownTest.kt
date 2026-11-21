package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import dev.phonefocus.domain.Tightening
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
    fun `format shows hours and minutes, rounded up so it never shows less than is left`() {
        assertEquals("23:42", ExitCountdown.format(Duration.ofHours(23).plusMinutes(41).plusSeconds(7)))
        assertEquals("23:41", ExitCountdown.format(Duration.ofHours(23).plusMinutes(41)))
        assertEquals("24:00", ExitCountdown.format(Duration.ofHours(24)))
        assertEquals("168:00", ExitCountdown.format(Duration.ofDays(7).minusSeconds(1)))
        assertEquals("1:05", ExitCountdown.format(Duration.ofMinutes(65)))
        assertEquals("0:01", ExitCountdown.format(Duration.ofSeconds(59)))
        assertEquals("0:01", ExitCountdown.format(Duration.ofMillis(1)))
        assertEquals("0:00", ExitCountdown.format(Duration.ZERO))
        assertEquals("0:00", ExitCountdown.format(Duration.ofSeconds(-5)))
    }

    @Test
    fun `formatWithSeconds shows hh mm ss, rounded up to the next second`() {
        assertEquals("23:41:07", ExitCountdown.formatWithSeconds(Duration.ofHours(23).plusMinutes(41).plusSeconds(7)))
        assertEquals("23:41:08", ExitCountdown.formatWithSeconds(Duration.ofHours(23).plusMinutes(41).plusSeconds(7).plusMillis(1)))
        assertEquals("24:00:00", ExitCountdown.formatWithSeconds(Duration.ofHours(24)))
        assertEquals("167:59:59", ExitCountdown.formatWithSeconds(Duration.ofDays(7).minusSeconds(1)))
        assertEquals("00:01:00", ExitCountdown.formatWithSeconds(Duration.ofMinutes(1)))
        assertEquals("00:00:01", ExitCountdown.formatWithSeconds(Duration.ofNanos(1)))
        assertEquals("00:00:00", ExitCountdown.formatWithSeconds(Duration.ZERO))
        assertEquals("00:00:00", ExitCountdown.formatWithSeconds(Duration.ofSeconds(-5)))
    }

    @Test
    fun `formatted texts never show less than the actual remaining time`() {
        listOf(0L, 1L, 999L, 1_000L, 59_999L, 60_000L, 60_001L, 86_399_999L).forEach { ms ->
            val d = Duration.ofMillis(ms)
            val (h, m, sec) = ExitCountdown.formatWithSeconds(d).split(":").map(String::toLong)
            assertTrue("$d", Duration.ofHours(h).plusMinutes(m).plusSeconds(sec) >= d)
            val (h2, m2) = ExitCountdown.format(d).split(":").map(String::toLong)
            assertTrue("$d", Duration.ofHours(h2).plusMinutes(m2) >= d)
        }
    }

    // --- status and progress -----------------------------------------------------------------

    @Test
    fun `no status while unlocked or focused`() = runBlocking {
        assertNull(countdown.status())
        commands.startFocus(AppPolicy.EMPTY, ExitDelay.ONE_DAY)
        assertNull(countdown.status())
    }

    @Test
    fun `progress is the elapsed fraction of the countdown`() = runBlocking {
        startCountdown()
        assertEquals(ExitCountdownStatus(Duration.ofHours(24), Duration.ofHours(24)), countdown.status())
        assertEquals(0f, countdown.status()!!.elapsedFraction, 0f)
        clock.advance(Duration.ofHours(6))
        val status = countdown.status()!!
        assertEquals(countdown.remaining(), status.remaining)
        assertEquals(0.25f, status.elapsedFraction, 1e-6f)
        clock.advance(Duration.ofHours(18))
        assertEquals(1f, countdown.status()!!.elapsedFraction, 0f)
        assertEquals(Duration.ZERO, countdown.status()!!.remaining)
    }

    @Test
    fun `progress ignores system time changes and reboots, like the unlock`() = runBlocking {
        startCountdown()
        clock.advance(Duration.ofHours(12))
        commands.tick()
        clock.changeSystemTime(start.plus(Duration.ofDays(30)))
        assertEquals(0.5f, countdown.status()!!.elapsedFraction, 1e-6f)
        clock.reboot(off = Duration.ofHours(10), upFor = Duration.ZERO)
        assertEquals(0.5f, countdown.status()!!.elapsedFraction, 1e-6f)
    }

    @Test
    fun `making the delay longer during a countdown keeps the countdown's length`() = runBlocking {
        startCountdown()
        clock.advance(Duration.ofHours(12))
        assertTrue(commands.tighten(Tightening(exitDelay = ExitDelay.SEVEN_DAYS)) is FocusResult.Success)
        commands.tick()
        val status = countdown.status()!!
        assertEquals(Duration.ofHours(24), status.total)
        assertEquals(0.5f, status.elapsedFraction, 1e-6f)
    }

    @Test
    fun `without a known start the exit delay is the length, never less than the time left`() {
        val reading = clock.read()
        val unlockAt = start.plus(Duration.ofHours(6))
        val session = FocusSession.restore(FocusState.ExitPending(unlockAt), AppPolicy.EMPTY, ExitDelay.ONE_DAY)
        val status = ExitCountdown.status(FocusRecord(session), reading)!!
        assertEquals(ExitCountdownStatus(Duration.ofHours(6), Duration.ofHours(24)), status)
        assertEquals(0.75f, status.elapsedFraction, 1e-6f)

        val short = FocusSession.restore(FocusState.ExitPending(unlockAt), AppPolicy.EMPTY, ExitDelay(Duration.ofHours(1)))
        assertEquals(0f, ExitCountdown.status(FocusRecord(short), reading)!!.elapsedFraction, 0f)
    }

    @Test
    fun `elapsed fraction stays within 0 and 1`() {
        assertEquals(0f, ExitCountdownStatus(Duration.ofHours(2), Duration.ofHours(1)).elapsedFraction, 0f)
        assertEquals(1f, ExitCountdownStatus(Duration.ZERO, Duration.ZERO).elapsedFraction, 0f)
        assertEquals(1f, ExitCountdownStatus(Duration.ofSeconds(-1), Duration.ofHours(1)).elapsedFraction, 0f)
    }
}
