package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.AppRuleOptions
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.ShortVideoApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration

class FocusSetupFormTest {
    private val essential = EssentialApps(setOf("dev.phonefocus", "org.dialer"))

    private fun entry(pkg: String, label: String = pkg) =
        AppCatalogueEntry(InstalledApp(pkg, label), AppRuleOptions.forPackage(pkg, essential), pkg in essential)

    private val dialer = entry("org.dialer")
    private val maps = entry("org.maps")
    private val instagram = entry(ShortVideoApps.INSTAGRAM)
    private val catalogue = listOf(dialer, instagram, maps)
    private val delays = ExitDelay.PRESETS

    private fun FocusSetupForm.ruleOf(pkg: String) = apps.single { it.packageName == pkg }.rule

    @Test
    fun `fresh session defaults to Blocked for every non-essential app and a one-day delay`() {
        val form = FocusSetupForm.create(catalogue, FocusSession.initial(), delays)
        assertEquals(AppRule.Allowed, form.ruleOf("org.dialer"))
        assertEquals(AppRule.Blocked, form.ruleOf("org.maps"))
        assertEquals(AppRule.Blocked, form.ruleOf(ShortVideoApps.INSTAGRAM))
        assertEquals(ExitDelay.ONE_DAY, form.exitDelay)
    }

    @Test
    fun `the last used policy and delay are the defaults`() {
        val last = AppPolicy(mapOf("org.maps" to AppRule.Allowed, ShortVideoApps.INSTAGRAM to AppRule.ShortVideoBlocked))
        val form = FocusSetupForm.create(catalogue, FocusSession.initial(last, ExitDelay.SEVEN_DAYS), delays)
        assertEquals(AppRule.Allowed, form.ruleOf("org.maps"))
        assertEquals(AppRule.ShortVideoBlocked, form.ruleOf(ShortVideoApps.INSTAGRAM))
        assertEquals(ExitDelay.SEVEN_DAYS, form.exitDelay)
    }

    @Test
    fun `apps keep the catalogue order`() {
        val form = FocusSetupForm.create(catalogue, FocusSession.initial(), delays)
        assertEquals(listOf("org.dialer", ShortVideoApps.INSTAGRAM, "org.maps"), form.apps.map { it.packageName })
    }

    @Test
    fun `essential apps are Allowed even if the last policy blocked them`() {
        val last = AppPolicy(mapOf("org.dialer" to AppRule.Blocked))
        val form = FocusSetupForm.create(catalogue, last, ExitDelay.ONE_DAY, delays)
        assertEquals(AppRule.Allowed, form.ruleOf("org.dialer"))
    }

    @Test
    fun `a remembered rule the app no longer offers falls back to a stricter offered one`() {
        // e.g. an app dropped from the short-video registry: No short videos -> Blocked, not Allowed.
        val last = AppPolicy(mapOf("org.maps" to AppRule.ShortVideoBlocked))
        val form = FocusSetupForm.create(catalogue, last, ExitDelay.ONE_DAY, delays)
        assertEquals(AppRule.Blocked, form.ruleOf("org.maps"))
    }

    @Test
    fun `edits override the defaults`() {
        val form = FocusSetupForm.create(
            catalogue,
            FocusSession.initial(),
            delays,
            ruleEdits = mapOf("org.maps" to AppRule.Allowed, ShortVideoApps.INSTAGRAM to AppRule.ShortVideoBlocked),
            delayEdit = ExitDelay.TWO_DAYS,
        )
        assertEquals(AppRule.Allowed, form.ruleOf("org.maps"))
        assertEquals(AppRule.ShortVideoBlocked, form.ruleOf(ShortVideoApps.INSTAGRAM))
        assertEquals(ExitDelay.TWO_DAYS, form.exitDelay)
    }

    @Test
    fun `edits that are not offered are ignored`() {
        val form = FocusSetupForm.create(
            catalogue,
            FocusSession.initial(),
            delays,
            ruleEdits = mapOf("org.maps" to AppRule.ShortVideoBlocked, "org.dialer" to AppRule.Blocked),
            delayEdit = ExitDelay(Duration.ofHours(3)),
        )
        assertEquals(AppRule.Blocked, form.ruleOf("org.maps"))
        assertEquals(AppRule.Allowed, form.ruleOf("org.dialer"))
        assertEquals(ExitDelay.ONE_DAY, form.exitDelay)
    }

    @Test
    fun `edits survive a catalogue change and apps that disappear are dropped`() {
        val edits = mapOf("org.maps" to AppRule.Allowed)
        val newApp = entry("org.new")
        val form = FocusSetupForm.create(listOf(maps, newApp), FocusSession.initial(), delays, edits)
        assertEquals(AppRule.Allowed, form.ruleOf("org.maps"))
        assertEquals(AppRule.Blocked, form.ruleOf("org.new"))
        assertEquals(listOf("org.maps", "org.new"), form.apps.map { it.packageName })
    }

    @Test
    fun `a remembered delay that is not offered picks the next longer one`() {
        val form = FocusSetupForm.create(catalogue, AppPolicy.EMPTY, ExitDelayOptions.DEBUG_ONE_MINUTE, delays)
        assertEquals(ExitDelay.TWELVE_HOURS, form.exitDelay)
        val longer = FocusSetupForm.create(catalogue, AppPolicy.EMPTY, ExitDelay(Duration.ofDays(30)), delays)
        assertEquals(ExitDelay.SEVEN_DAYS, longer.exitDelay)
    }

    @Test
    fun `the debug delay is kept when it is offered`() {
        val form = FocusSetupForm.create(
            catalogue,
            AppPolicy.EMPTY,
            ExitDelayOptions.DEBUG_ONE_MINUTE,
            ExitDelayOptions.available(debugBuild = true),
        )
        assertEquals(ExitDelayOptions.DEBUG_ONE_MINUTE, form.exitDelay)
    }

    @Test
    fun `policy lists the chosen rule for every app on the screen and nothing else`() {
        val last = AppPolicy(mapOf("org.uninstalled" to AppRule.Allowed, "org.maps" to AppRule.Allowed))
        val form = FocusSetupForm.create(catalogue, last, ExitDelay.ONE_DAY, delays)
        assertEquals(
            AppPolicy(
                mapOf(
                    "org.dialer" to AppRule.Allowed,
                    "org.maps" to AppRule.Allowed,
                    ShortVideoApps.INSTAGRAM to AppRule.Blocked,
                ),
            ),
            form.policy(),
        )
        assertFalse("org.uninstalled" in form.policy().rules)
    }

    @Test
    fun `the policy is always accepted by startFocus`() {
        val form = FocusSetupForm.create(
            catalogue,
            FocusSession.initial(),
            delays,
            ruleEdits = mapOf(ShortVideoApps.INSTAGRAM to AppRule.ShortVideoBlocked),
        )
        assertEquals(emptyList<Any>(), form.policy().unsupportedRulesIn())
    }
}
