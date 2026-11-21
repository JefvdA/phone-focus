package dev.phonefocus.application

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

class FakeRepository(initial: FocusRecord = FocusRecord.INITIAL) : FocusSessionRepository {
    val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    var writes = 0
        private set

    override val records: Flow<FocusRecord> = state

    override suspend fun update(transform: (FocusRecord) -> FocusRecord): FocusRecord = mutex.withLock {
        transform(state.value).also {
            if (it != state.value) writes++
            state.value = it
        }
    }
}

/** A device whose wall clock can be changed and which can be rebooted. */
class FakeDeviceClock(@Volatile var wallTime: Instant) : DeviceClock {
    var elapsedRealtime: Duration = Duration.ofMinutes(5)
    var bootCount: Int = 1

    override fun read() = ClockReading(wallTime, elapsedRealtime, bootCount)

    /** Real time passes: both clocks advance. */
    fun advance(d: Duration) {
        wallTime += d
        elapsedRealtime += d
    }

    /** The user changes the system time; the monotonic clock is unaffected. */
    fun changeSystemTime(t: Instant) {
        wallTime = t
    }

    /** Phone is off for [off], then boots and runs for [upFor] (wall clock keeps running). */
    fun reboot(off: Duration, upFor: Duration = Duration.ofSeconds(30)) {
        wallTime += off + upFor
        elapsedRealtime = upFor
        bootCount++
    }
}

class FakeScheduler : UnlockScheduler {
    /** The pending unlock delay, null when nothing is scheduled. */
    var scheduled: Duration? = null
    val calls = mutableListOf<String>()

    @Synchronized
    override fun scheduleUnlockIn(delay: Duration) {
        scheduled = delay
        calls += "schedule $delay"
    }

    @Synchronized
    override fun cancel() {
        scheduled = null
        calls += "cancel"
    }
}
