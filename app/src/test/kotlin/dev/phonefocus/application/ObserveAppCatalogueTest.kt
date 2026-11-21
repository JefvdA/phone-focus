package dev.phonefocus.application

import dev.phonefocus.domain.AppAccess
import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
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

class ObserveAppCatalogueTest {
    private class FakeInstalledApps(initial: List<InstalledApp>) : InstalledAppsSource {
        val state = MutableStateFlow(initial)
        override val apps: Flow<List<InstalledApp>> = state
    }

    private class FakeEssentialApps(initial: EssentialApps) : EssentialAppsSource {
        val state = MutableStateFlow(initial)
        override val essentialApps: Flow<EssentialApps> = state
    }

    private val self = InstalledApp("dev.phonefocus", "Phone Focus")
    private val settings = InstalledApp("com.android.settings", "Settings")
    private val dialer = InstalledApp("com.samsung.android.dialer", "Phone")
    private val instagram = InstalledApp(ShortVideoApps.INSTAGRAM, "Instagram")
    private val youtube = InstalledApp(ShortVideoApps.YOUTUBE, "YouTube")
    private val whatsapp = InstalledApp("com.whatsapp", "WhatsApp")
    private val calculator = InstalledApp("com.sec.android.app.popupcalculator", "calculator")

    private val installed = FakeInstalledApps(listOf(youtube, whatsapp, self, instagram, settings, dialer, calculator))
    private val essential = FakeEssentialApps(EssentialApps(setOf(self, settings, dialer).map { it.packageName }.toSet()))
    private val catalogue = ObserveAppCatalogue(installed, essential)

    private fun current() = runBlocking { catalogue().first() }

    @Test
    fun `lists every installed app once, sorted by label ignoring case`() {
        installed.state.value = installed.state.value + whatsapp
        assertEquals(
            listOf("calculator", "Instagram", "Phone", "Phone Focus", "Settings", "WhatsApp", "YouTube"),
            current().map { it.app.label },
        )
    }

    @Test
    fun `short-video apps get three options, other apps two, essential apps only Allowed`() {
        val byPackage = current().associateBy { it.app.packageName }
        val three = listOf(AppRule.Allowed, AppRule.ShortVideoBlocked, AppRule.Blocked)
        assertEquals(three, byPackage.getValue(instagram.packageName).ruleOptions)
        assertEquals(three, byPackage.getValue(youtube.packageName).ruleOptions)
        assertEquals(listOf(AppRule.Allowed, AppRule.Blocked), byPackage.getValue(whatsapp.packageName).ruleOptions)
        listOf(self, settings, dialer).forEach {
            val entry = byPackage.getValue(it.packageName)
            assertTrue(entry.essential)
            assertEquals(listOf(AppRule.Allowed), entry.ruleOptions)
        }
        assertFalse(byPackage.getValue(whatsapp.packageName).essential)
    }

    @Test
    fun `reacts to installs and uninstalls`() {
        val tiktok = InstalledApp("com.zhiliaoapp.musically", "TikTok")
        installed.state.value = installed.state.value + tiktok - whatsapp
        val packages = current().map { it.app.packageName }
        assertTrue(tiktok.packageName in packages)
        assertFalse(whatsapp.packageName in packages)
        assertEquals(
            listOf(AppRule.Allowed, AppRule.Blocked),
            current().first { it.app == tiktok }.ruleOptions,
        )
    }

    @Test
    fun `follows a change of the default dialer`() {
        val googleDialer = InstalledApp("com.google.android.dialer", "Phone by Google")
        installed.state.value = installed.state.value + googleDialer
        essential.state.value = EssentialApps(setOf(self.packageName, settings.packageName, googleDialer.packageName))
        val byPackage = current().associateBy { it.app.packageName }
        assertTrue(byPackage.getValue(googleDialer.packageName).essential)
        assertFalse(byPackage.getValue(dialer.packageName).essential)
    }

    @Test
    fun `an app installed during focus stays Blocked and is never added to the policy`() = runBlocking {
        val repo = FakeRepository()
        val commands = FocusCommands(repo, FakeDeviceClock(Instant.parse("2026-01-01T08:00:00Z")), FakeScheduler())
        val policy = AppPolicy(mapOf(whatsapp.packageName to AppRule.Allowed))
        assertTrue(commands.startFocus(policy, ExitDelay.ONE_DAY) is FocusResult.Success)
        val writesBefore = repo.writes

        val newApp = InstalledApp("com.new.game", "New Game")
        installed.state.value = installed.state.value + newApp
        val entry = catalogue().first().first { it.app == newApp }

        assertEquals(writesBefore, repo.writes)
        val session = repo.state.value.session
        assertEquals(policy, session.policy)
        assertEquals(AppRule.Blocked, session.policy.ruleFor(newApp.packageName))
        val access = AppAccess(session, essential.state.value)
        assertFalse(access.canLaunch(newApp.packageName))
        assertFalse(newApp.packageName in access.allowedApps())
        assertFalse(entry.essential)
    }
}
