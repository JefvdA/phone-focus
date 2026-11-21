package dev.phonefocus.application

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TrustedTimeTest {
    private val t0 = Instant.parse("2026-01-01T08:00:00Z")
    private val checkpoint = TimeCheckpoint(t0, Duration.ofHours(1), bootCount = 3)

    @Test
    fun `without a checkpoint the wall clock is used`() {
        val reading = ClockReading(t0, Duration.ofHours(5), 3)
        assertEquals(t0, TrustedTime.now(reading, null))
    }

    @Test
    fun `same boot measures from the checkpoint with the monotonic clock`() {
        val reading = ClockReading(t0.plus(Duration.ofDays(30)), Duration.ofHours(3), 3)
        assertEquals(t0.plus(Duration.ofHours(2)), TrustedTime.now(reading, checkpoint))
    }

    @Test
    fun `same boot ignores the wall clock being set back`() {
        val reading = ClockReading(t0.minus(Duration.ofDays(30)), Duration.ofHours(3), 3)
        assertEquals(t0.plus(Duration.ofHours(2)), TrustedTime.now(reading, checkpoint))
    }

    @Test
    fun `after a reboot only the time since boot is counted`() {
        val reading = ClockReading(t0.plus(Duration.ofDays(30)), Duration.ofMinutes(2), 4)
        assertEquals(t0.plus(Duration.ofMinutes(2)), TrustedTime.now(reading, checkpoint))
    }

    @Test
    fun `a stale reading from the same known boot counts no time, it is not a reboot`() {
        val reading = ClockReading(t0.plus(Duration.ofDays(30)), Duration.ofMinutes(59), 3)
        assertEquals(t0, TrustedTime.now(reading, checkpoint))
    }

    @Test
    fun `without a boot count a monotonic clock that went back is treated as a reboot`() {
        val unknown = TrustedTime.UNKNOWN_BOOT_COUNT
        val cp = checkpoint.copy(bootCount = unknown)
        assertEquals(t0.plus(Duration.ofMinutes(2)), TrustedTime.now(ClockReading(t0, Duration.ofMinutes(2), unknown), cp))
        assertEquals(t0.plus(Duration.ofHours(1)), TrustedTime.now(ClockReading(t0, Duration.ofHours(2), unknown), cp))
    }

    @Test
    fun `a known boot count on one side only falls back to the monotonic clock check`() {
        val reading = ClockReading(t0, Duration.ofHours(2), 3)
        val cp = checkpoint.copy(bootCount = TrustedTime.UNKNOWN_BOOT_COUNT)
        assertEquals(t0.plus(Duration.ofHours(1)), TrustedTime.now(reading, cp))
    }

    @Test
    fun `checkpoint records the reading and the trusted time`() {
        val reading = ClockReading(t0, Duration.ofHours(7), 9)
        assertEquals(TimeCheckpoint(t0.plusSeconds(1), Duration.ofHours(7), 9), TrustedTime.checkpoint(reading, t0.plusSeconds(1)))
    }
}
