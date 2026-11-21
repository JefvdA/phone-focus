package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CoverScreenTest {
    private val dialer = "com.samsung.android.dialer"
    private val music = "com.sec.android.app.music"
    private val camera = "com.sec.android.app.camera"
    private val clockApp = "com.sec.android.app.clockpackage"

    private val resolved = mutableMapOf<QuickAction, String?>(
        QuickAction.Call to dialer,
        QuickAction.Music to music,
        QuickAction.Camera to camera,
        QuickAction.Timer to clockApp,
    )
    private val resolver = QuickActionResolver { resolved[it] }
    private val essential = EssentialApps(setOf("dev.phonefocus", "com.android.settings", dialer))
    private val essentialSource = object : EssentialAppsSource {
        override val essentialApps: Flow<EssentialApps> = MutableStateFlow(essential)
    }

    private val start = Instant.parse("2026-03-01T10:00:00Z")
    private val clock = FakeDeviceClock(start)
    private val repository = FakeRepository()
    private val commands = FocusCommands(repository, clock, FakeScheduler())
    private val observe = ObserveCoverScreen(repository, clock, essentialSource, resolver)
    private val check = CheckQuickAction(ObserveFocusSession(repository), essentialSource, resolver)

    private val policy = AppPolicy(mapOf(music to AppRule.Allowed, camera to AppRule.Blocked))

    private suspend fun focus(p: AppPolicy = policy, delay: ExitDelay = ExitDelay.ONE_DAY) {
        assertTrue(commands.startFocus(p, delay) is FocusResult.Success)
    }

    private fun CoverScreen.enabled(action: QuickAction) = buttons.single { it.action == action }.enabled

    @Test
    fun `unlocked shows Unlocked and every resolvable button`() = runBlocking {
        val screen = observe.current()
        assertEquals(CoverStatus.Unlocked, screen.status)
        assertEquals("Unlocked", screen.status.text)
        assertEquals(QuickAction.DEFAULTS, screen.buttons.map { it.action })
        assertTrue(screen.buttons.all { it.enabled })
        assertNull(screen.nextRefreshIn)
    }

    @Test
    fun `default buttons are Call, Music, Camera, Timer`() {
        assertEquals(listOf("Call", "Music", "Camera", "Timer"), QuickAction.DEFAULTS.map { it.label })
    }

    @Test
    fun `focused enables only buttons whose app AppAccess allows`() = runBlocking {
        focus()
        val screen = observe.current()
        assertEquals("Focus", screen.status.text)
        assertTrue("dialer is essential", screen.enabled(QuickAction.Call))
        assertTrue("allowed", screen.enabled(QuickAction.Music))
        assertFalse("blocked", screen.enabled(QuickAction.Camera))
        assertFalse("unlisted = blocked", screen.enabled(QuickAction.Timer))
        assertNull(screen.nextRefreshIn)
    }

    @Test
    fun `Call stays enabled even when the policy blocks the dialer`() = runBlocking {
        focus(AppPolicy(mapOf(dialer to AppRule.Blocked)))
        assertTrue(observe.current().enabled(QuickAction.Call))
    }

    @Test
    fun `a button without an app is disabled, also while unlocked`() = runBlocking {
        resolved[QuickAction.Music] = null
        assertFalse(observe.current().enabled(QuickAction.Music))
        assertNull(check(QuickAction.Music))
    }

    @Test
    fun `exit pending shows the time left rounded up to the minute`() = runBlocking {
        focus()
        commands.requestExit()
        assertEquals("Exit in 24:00", observe.current().status.text)
        clock.advance(Duration.ofMinutes(18).plusSeconds(53)) // 23:41:07 left
        val screen = observe.current()
        assertEquals(CoverStatus.ExitPending(Duration.ofHours(23).plusMinutes(41).plusSeconds(7)), screen.status)
        assertEquals("Exit in 23:42", screen.status.text)
        assertEquals("shows 23:41 from 23:41:00 left", Duration.ofSeconds(7), screen.nextRefreshIn)
    }

    @Test
    fun `exit pending is measured with trusted time, not the changeable wall clock`() = runBlocking {
        focus()
        commands.requestExit()
        clock.advance(Duration.ofHours(1))
        clock.changeSystemTime(start.plus(Duration.ofDays(3)))
        assertEquals("Exit in 23:00", observe.current().status.text)
    }

    @Test
    fun `the shown countdown never shows less than the time actually left`() {
        // Every second over the last 3 minutes and around an hour boundary.
        val samples = (0L..180L).map { Duration.ofSeconds(it) } +
            (3590L..3610L).map { Duration.ofSeconds(it) } +
            listOf(Duration.ofMillis(1), Duration.ofSeconds(59).plusMillis(999), Duration.ofDays(7))
        for (left in samples) {
            val text = CoverStatus.ExitPending(left).text.removePrefix("Exit in ")
            val (h, m) = text.split(":").map(String::toLong)
            val shown = Duration.ofHours(h).plusMinutes(m)
            assertTrue("$left shown as $text", shown >= left)
            assertTrue("$left shown as $text: at most a minute more", shown - left < Duration.ofMinutes(1))
        }
    }

    @Test
    fun `next refresh is exactly when the shown minute goes down`() {
        assertEquals(Duration.ofSeconds(1), CoverScreen.nextMinuteChange(Duration.ofSeconds(61)))
        assertEquals(Duration.ofMinutes(1), CoverScreen.nextMinuteChange(Duration.ofMinutes(2)))
        assertEquals(Duration.ofSeconds(30), CoverScreen.nextMinuteChange(Duration.ofSeconds(30)))
        assertEquals(Duration.ofMillis(500), CoverScreen.nextMinuteChange(Duration.ofSeconds(60).plusMillis(500)))
        assertNull(CoverScreen.nextMinuteChange(Duration.ZERO))
        assertNull(CoverScreen.nextMinuteChange(Duration.ofSeconds(-5)))
        // After the refresh the text shows one minute less, still not less than what is left.
        for (s in listOf(1L, 59L, 60L, 61L, 3600L, 86_399L)) {
            val left = Duration.ofSeconds(s).plusMillis(250)
            val after = left - CoverScreen.nextMinuteChange(left)!!
            assertEquals(
                ExitCountdown.format(left).let { minutesOf(it) - 1 },
                minutesOf(ExitCountdown.format(after)),
            )
        }
    }

    private fun minutesOf(text: String): Long = text.split(":").let { (h, m) -> h.toLong() * 60 + m.toLong() }

    @Test
    fun `an ended countdown shows zero and needs no refresh`() = runBlocking {
        focus()
        commands.requestExit()
        clock.advance(Duration.ofDays(2))
        val screen = observe.current()
        assertEquals("Exit in 0:00", screen.status.text)
        assertNull(screen.nextRefreshIn)
    }

    @Test
    fun `the flow updates when the session changes`() = runBlocking {
        val first = observe().first()
        assertEquals(CoverStatus.Unlocked, first.status)
        focus()
        assertEquals(CoverStatus.Focus, observe().first().status)
    }

    @Test
    fun `the flow re-emits when the shown minute goes down`() = runBlocking {
        focus()
        commands.requestExit()
        clock.advance(Duration.ofHours(24).minusMillis(60_050)) // 1:00.05 left: shows 0:02
        // The fake clock does not move by itself; a quick second emission still proves the loop
        // re-reads the clock after nextRefreshIn (50 ms).
        val emissions = mutableListOf<CoverScreen>()
        val job = launch {
            observe().take(2).toList(emissions)
        }
        delay(20)
        clock.advance(Duration.ofMillis(100)) // 0:59.95 left: shows 0:01
        job.join()
        assertEquals(listOf("Exit in 0:02", "Exit in 0:01"), emissions.map { it.status.text })
    }

    @Test
    fun `quick actions are checked against AppAccess on every tap`() = runBlocking {
        assertEquals(camera, check(QuickAction.Camera))
        focus()
        assertEquals(dialer, check(QuickAction.Call))
        assertEquals(music, check(QuickAction.Music))
        assertNull(check(QuickAction.Camera))
        assertNull(check(QuickAction.Timer))
    }
}
