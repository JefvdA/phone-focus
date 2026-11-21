package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.ShortVideoApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AppBlockingTest {
    private val self = "dev.phonefocus"
    private val settings = "com.android.settings"
    private val dialer = "com.example.dialer"
    private val whatsapp = "com.whatsapp"
    private val youtube = ShortVideoApps.YOUTUBE
    private val instagram = ShortVideoApps.INSTAGRAM
    private val newApp = "com.example.newgame"
    private val keyboard = "com.example.keyboard"
    private val serviceOnly = "com.google.android.gms"

    private val policy = AppPolicy(
        mapOf(
            whatsapp to AppRule.Allowed,
            instagram to AppRule.ShortVideoBlocked,
            youtube to AppRule.Blocked,
            settings to AppRule.Blocked,
        ),
    )

    private val essential = EssentialApps(setOf(self, settings, dialer))
    private val launchable = setOf(self, settings, dialer, whatsapp, youtube, instagram, newApp, keyboard, "com.android.systemui")

    private fun focused(): FocusSession =
        (FocusSession.initial().startFocus(policy, ExitDelay.ONE_DAY) as FocusResult.Success).session

    private fun exitPending(): FocusSession =
        (focused().requestExit(Instant.parse("2026-01-01T00:00:00Z")) as FocusResult.Success).session

    private fun context(session: FocusSession = focused()) =
        AppBlockingContext(session, essential, systemPackages = setOf(keyboard), launchablePackages = launchable)

    // --- verdict --------------------------------------------------------------------------

    @Test
    fun `blocked apps bounce while focused and while an exit is pending`() {
        assertEquals(ForegroundVerdict.Blocked, context().verdict(youtube))
        assertEquals(ForegroundVerdict.Blocked, context(exitPending()).verdict(youtube))
        assertTrue(ForegroundVerdict.Blocked.bounces)
    }

    @Test
    fun `unlisted (unknown, newly installed) apps are blocked`() {
        assertEquals(ForegroundVerdict.Blocked, context().verdict(newApp))
    }

    @Test
    fun `allowed and no-short-video apps stay`() {
        assertEquals(ForegroundVerdict.Allowed, context().verdict(whatsapp))
        assertEquals(ForegroundVerdict.Allowed, context().verdict(instagram))
    }

    @Test
    fun `essential apps stay even when the policy blocks them`() {
        assertEquals(ForegroundVerdict.Allowed, context().verdict(self))
        assertEquals(ForegroundVerdict.Allowed, context().verdict(settings))
        assertEquals(ForegroundVerdict.Allowed, context().verdict(dialer))
    }

    @Test
    fun `fixed and device-specific system packages are never blocked, even with a launcher entry`() {
        assertEquals(ForegroundVerdict.NeverBlocked, context().verdict("com.android.systemui"))
        assertEquals(ForegroundVerdict.NeverBlocked, context().verdict("com.android.server.telecom"))
        assertEquals(ForegroundVerdict.NeverBlocked, context().verdict(keyboard))
    }

    @Test
    fun `packages without a launcher entry are not judged as apps`() {
        assertEquals(ForegroundVerdict.NotAnApp, context().verdict(serviceOnly))
    }

    @Test
    fun `nothing is blocked while unlocked`() {
        val unlocked = context(FocusSession.initial())
        listOf(youtube, newApp, serviceOnly, whatsapp).forEach {
            assertEquals(ForegroundVerdict.Unlocked, unlocked.verdict(it))
        }
    }

    @Test
    fun `only Blocked bounces`() {
        assertEquals(listOf(ForegroundVerdict.Blocked), ForegroundVerdict.entries.filter { it.bounces })
    }

    // --- EvaluateForegroundApp ------------------------------------------------------------

    private fun screen(vararg packages: String, desktopInFront: Boolean = false) =
        ForegroundScreen(packages.map { ForegroundWindow(it) }, desktopInFront)

    private fun pip(packageName: String) = ForegroundWindow(packageName, pictureInPicture = true)

    private fun cover(packageName: String) = ForegroundWindow(packageName, defaultDisplay = false)

    @Test
    fun `bounces a blocked app in front`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        assertEquals(ForegroundAction.Bounce(youtube), evaluate(context(), screen(youtube), 1_000))
    }

    @Test
    fun `leaves allowed, essential, system and unlocked apps alone`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        assertEquals(ForegroundAction.None, evaluate(context(), screen(whatsapp, self, "com.android.systemui"), 1_000))
        assertEquals(ForegroundAction.None, evaluate(context(FocusSession.initial()), screen(youtube), 1_000))
        assertEquals(ForegroundAction.None, evaluate(context(), screen(), 1_000))
    }

    @Test
    fun `bounces when any app in split screen is blocked`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        assertEquals(ForegroundAction.Bounce(youtube), evaluate(context(), screen(whatsapp, youtube), 1_000))
    }

    @Test
    fun `repeated events for the same app within the debounce ask for a recheck instead of bouncing again`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        assertEquals(ForegroundAction.Bounce(youtube), evaluate(context(), screen(youtube), 1_000))
        assertEquals(ForegroundAction.RecheckIn(400), evaluate(context(), screen(youtube), 1_100))
        assertEquals(ForegroundAction.RecheckIn(1), evaluate(context(), screen(youtube), 1_499))
    }

    @Test
    fun `bounces the same app again once the debounce is over`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        evaluate(context(), screen(youtube), 1_000)
        assertEquals(ForegroundAction.Bounce(youtube), evaluate(context(), screen(youtube), 1_500))
    }

    @Test
    fun `a different blocked app bounces at once`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        evaluate(context(), screen(youtube), 1_000)
        assertEquals(ForegroundAction.Bounce(newApp), evaluate(context(), screen(newApp), 1_100))
    }

    @Test
    fun `a clock going backwards does not suppress bounces`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        evaluate(context(), screen(youtube), 1_000)
        assertEquals(ForegroundAction.Bounce(youtube), evaluate(context(), screen(youtube), 900))
    }

    @Test
    fun `without a context yet it asks for a recheck`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        assertEquals(ForegroundAction.RecheckIn(500), evaluate(null, screen(youtube), 1_000))
        assertEquals(ForegroundAction.None, evaluate(null, screen(), 1_000))
    }

    @Test
    fun `a blocked app that keeps reappearing is bounced once per debounce, with rechecks in between`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        val actions = (0L..1_500L step 100).map { t -> evaluate(context(), screen(youtube), 1_000 + t) }
        assertEquals(4, actions.count { it is ForegroundAction.Bounce })
        assertTrue(actions.all { it is ForegroundAction.Bounce || it is ForegroundAction.RecheckIn })
    }

    @Test
    fun `never restarts the desktop while it is in front, only rechecks`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        repeat(10) { i ->
            assertEquals(
                ForegroundAction.RecheckIn(500),
                evaluate(context(), screen(self, youtube, desktopInFront = true), 1_000L + i * 600),
            )
        }
    }

    @Test
    fun `a blocked picture-in-picture window is dismissed, not bounced`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        val screen = ForegroundScreen(listOf(ForegroundWindow(self), pip(youtube)), desktopInFront = true)
        assertEquals(ForegroundAction.DismissPictureInPicture(youtube), evaluate(context(), screen, 1_000))
    }

    @Test
    fun `a picture-in-picture window that stays never restarts the desktop`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        val screen = ForegroundScreen(listOf(ForegroundWindow(self), pip(youtube)), desktopInFront = true)
        val actions = (0L..3_000L step 100).map { t -> evaluate(context(), screen, 1_000 + t) }
        assertTrue(actions.none { it is ForegroundAction.Bounce })
        assertEquals(7, actions.count { it is ForegroundAction.DismissPictureInPicture })
    }

    @Test
    fun `allowed picture-in-picture windows are left alone`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        assertEquals(ForegroundAction.None, evaluate(context(), ForegroundScreen(listOf(pip(whatsapp))), 1_000))
    }

    @Test
    fun `a blocked full-screen app is bounced before a blocked picture-in-picture is dismissed`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        val screen = ForegroundScreen(listOf(pip(youtube), ForegroundWindow(newApp)))
        assertEquals(ForegroundAction.Bounce(newApp), evaluate(context(), screen, 1_000))
        assertEquals(ForegroundAction.DismissPictureInPicture(youtube), evaluate(context(), ForegroundScreen(listOf(pip(youtube))), 1_100))
    }

    @Test
    fun `windows on another display (cover screen) are not judged, so they cannot loop`() {
        val evaluate = EvaluateForegroundApp(debounceMillis = 500)
        assertEquals(ForegroundAction.None, evaluate(context(), ForegroundScreen(listOf(cover(youtube))), 1_000))
        assertEquals(ForegroundAction.None, evaluate(null, ForegroundScreen(listOf(cover(youtube))), 1_000))
        val mixed = ForegroundScreen(listOf(cover(youtube), ForegroundWindow(newApp)))
        assertEquals(ForegroundAction.Bounce(newApp), evaluate(context(), mixed, 1_000))
    }

    // --- ObserveAppBlocking / warning -----------------------------------------------------

    private fun repositoryWith(session: FocusSession) = FakeRepository(FocusRecord(session))

    @Test
    fun `ObserveAppBlocking combines session, essential, system and launchable apps`() = runBlocking {
        val observe = ObserveAppBlocking(
            ObserveFocusSession(repositoryWith(focused())),
            object : EssentialAppsSource { override val essentialApps = flowOf(essential) },
            object : SystemPackagesSource { override val systemPackages = flowOf(setOf(keyboard)) },
            object : InstalledAppsSource {
                override val apps = flowOf(launchable.map { InstalledApp(it, it) })
            },
        )
        assertEquals(context(), observe().first())
    }

    private class FakeBlockerStatus(initial: PrerequisiteStatus) : AppBlockerStatusSource {
        override val status = MutableStateFlow(initial)
        var refreshes = 0
        override fun refresh() {
            refreshes++
        }
    }

    @Test
    fun `warns when focus is on and the service is off`() = runBlocking {
        val warning = ObserveAppBlockerWarning(
            ObserveFocusSession(repositoryWith(focused())),
            FakeBlockerStatus(PrerequisiteStatus.Missing),
        )
        assertTrue(warning().first())
    }

    @Test
    fun `no warning when the service is on, unavailable, or focus is off`() = runBlocking {
        val focusedOn = ObserveFocusSession(repositoryWith(focused()))
        assertFalse(ObserveAppBlockerWarning(focusedOn, FakeBlockerStatus(PrerequisiteStatus.Met))().first())
        assertFalse(ObserveAppBlockerWarning(focusedOn, FakeBlockerStatus(PrerequisiteStatus.NotAvailable))().first())
        val unlocked = ObserveFocusSession(repositoryWith(FocusSession.initial()))
        assertFalse(ObserveAppBlockerWarning(unlocked, FakeBlockerStatus(PrerequisiteStatus.Missing))().first())
    }

    @Test
    fun `the warning follows the service being turned off during focus`() = runBlocking {
        val status = FakeBlockerStatus(PrerequisiteStatus.Met)
        val warning = ObserveAppBlockerWarning(ObserveFocusSession(repositoryWith(exitPending())), status)
        assertFalse(warning().first())
        status.status.value = PrerequisiteStatus.Missing
        assertTrue(warning().first())
    }
}
