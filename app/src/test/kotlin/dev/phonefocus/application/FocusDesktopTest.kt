package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.ShortVideoApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FocusDesktopTest {
    private val self = InstalledApp("dev.phonefocus", "Phone Focus")
    private val settings = InstalledApp("com.android.settings", "Settings")
    private val dialer = InstalledApp("com.samsung.android.dialer", "Phone")
    private val instagram = InstalledApp(ShortVideoApps.INSTAGRAM, "Instagram")
    private val youtube = InstalledApp(ShortVideoApps.YOUTUBE, "YouTube")
    private val whatsapp = InstalledApp("com.whatsapp", "WhatsApp")
    private val calculator = InstalledApp("com.example.calculator", "calculator")
    private val browser = InstalledApp("com.example.browser", "Browser")

    private val installed = listOf(youtube, whatsapp, self, instagram, settings, dialer, calculator, browser)
    private val essential = EssentialApps(setOf(self.packageName, settings.packageName, dialer.packageName))

    private val policy = AppPolicy(
        mapOf(
            whatsapp.packageName to AppRule.Allowed,
            instagram.packageName to AppRule.ShortVideoBlocked,
            youtube.packageName to AppRule.Blocked,
            // calculator unlisted: Blocked. Settings listed as Blocked: still essential.
            settings.packageName to AppRule.Blocked,
            browser.packageName to AppRule.Allowed,
        ),
    )

    private fun focused(p: AppPolicy = policy): FocusSession =
        (FocusSession.initial().startFocus(p, ExitDelay.ONE_DAY) as FocusResult.Success).session

    private fun FocusDesktop.packages() = apps.map { it.packageName }

    @Test
    fun `focused desktop shows allowed and no-short-video apps plus essential apps, sorted by label`() {
        val desktop = FocusDesktop.of(focused(), installed, essential, self.packageName)
        assertEquals(listOf("Browser", "Instagram", "Phone", "Settings", "WhatsApp"), desktop.apps.map { it.label })
    }

    @Test
    fun `blocked and unlisted apps are not on the desktop`() {
        val desktop = FocusDesktop.of(focused(), installed, essential, self.packageName)
        assertFalse(youtube.packageName in desktop.packages())
        assertFalse(calculator.packageName in desktop.packages())
    }

    @Test
    fun `this app is not on its own desktop although it is essential`() {
        val desktop = FocusDesktop.of(focused(), installed, essential, self.packageName)
        assertFalse(self.packageName in desktop.packages())
    }

    @Test
    fun `allowed apps that are not installed are not shown`() {
        val desktop = FocusDesktop.of(focused(), installed - browser, essential, self.packageName)
        assertFalse(browser.packageName in desktop.packages())
    }

    @Test
    fun `the exit countdown keeps the same apps`() {
        val pending = (focused().requestExit(Instant.parse("2026-01-01T00:00:00Z")) as FocusResult.Success).session
        assertEquals(
            FocusDesktop.of(focused(), installed, essential, self.packageName).apps,
            FocusDesktop.of(pending, installed, essential, self.packageName).apps,
        )
    }

    @Test
    fun `unlocked desktop shows no apps`() {
        val desktop = FocusDesktop.of(FocusSession.initial(policy), installed, essential, self.packageName)
        assertTrue(desktop.apps.isEmpty())
    }

    @Test
    fun `duplicate packages are shown once`() {
        val desktop = FocusDesktop.of(focused(), installed + whatsapp, essential, self.packageName)
        assertEquals(1, desktop.packages().count { it == whatsapp.packageName })
    }

    private class FakeInstalledApps(initial: List<InstalledApp>) : InstalledAppsSource {
        val state = MutableStateFlow(initial)
        override val apps: Flow<List<InstalledApp>> = state
    }

    private class FakeEssentialApps(initial: EssentialApps) : EssentialAppsSource {
        val state = MutableStateFlow(initial)
        override val essentialApps: Flow<EssentialApps> = state
    }

    @Test
    fun `observed desktop follows the session and installed apps`() = runBlocking {
        val repository = FakeRepository()
        val apps = FakeInstalledApps(installed)
        val observe = ObserveFocusDesktop(ObserveFocusSession(repository), apps, FakeEssentialApps(essential), self.packageName)

        assertTrue(observe().first().apps.isEmpty())

        repository.state.value = FocusRecord(focused())
        assertTrue(whatsapp.packageName in observe().first().packages())

        apps.state.value = installed - whatsapp
        assertFalse(whatsapp.packageName in observe().first().packages())
    }

    @Test
    fun `launch check uses AppAccess with the current session`() = runBlocking {
        val repository = FakeRepository()
        val check = CheckAppLaunch(ObserveFocusSession(repository), FakeEssentialApps(essential))

        // Unlocked: anything may launch.
        assertTrue(check(youtube.packageName))

        repository.state.value = FocusRecord(focused())
        assertTrue(check(whatsapp.packageName))
        assertTrue(check(instagram.packageName))
        assertFalse(check(youtube.packageName))
        assertFalse(check(calculator.packageName))
        // Essential apps are never blocked, whatever the policy says.
        assertTrue(check(settings.packageName))
        assertTrue(check(dialer.packageName))
    }
}
