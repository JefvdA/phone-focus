package dev.phonefocus.infrastructure.time

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import dev.phonefocus.application.ClockReading
import dev.phonefocus.application.DeviceClock
import dev.phonefocus.application.TrustedTime
import java.time.Duration
import java.time.Instant

/** Reads the wall clock, `SystemClock.elapsedRealtime()` and the boot count. */
class AndroidDeviceClock(private val contentResolver: ContentResolver) : DeviceClock {
    override fun read(): ClockReading = ClockReading(
        wallTime = Instant.now(),
        elapsedRealtime = Duration.ofMillis(SystemClock.elapsedRealtime()),
        // If unavailable, a reboot is still detected by elapsedRealtime going back.
        bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, TrustedTime.UNKNOWN_BOOT_COUNT),
    )
}
