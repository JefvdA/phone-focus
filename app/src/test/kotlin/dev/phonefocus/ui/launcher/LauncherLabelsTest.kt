package dev.phonefocus.ui.launcher

import dev.phonefocus.application.AppCatalogueEntry
import dev.phonefocus.application.InstalledApp
import dev.phonefocus.application.MakeStricterForm
import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusCommand
import dev.phonefocus.domain.FocusError
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import dev.phonefocus.domain.Loosening
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class LauncherLabelsTest {
    @Test
    fun `focus button shows Focus, or the remaining time while exit is pending`() {
        assertEquals("Focus", LauncherLabels.focusButton(null))
        assertEquals("Exit 23:41", LauncherLabels.focusButton(Duration.ofHours(23).plusMinutes(41).plusSeconds(7)))
    }

    @Test
    fun `exit delay label`() {
        assertEquals("Exit delay: 24 hours", LauncherLabels.exitDelay(ExitDelay.ONE_DAY))
        assertEquals("Exit delay: 7 days", LauncherLabels.exitDelay(ExitDelay.SEVEN_DAYS))
    }

    @Test
    fun `make stricter lists the delay and app changes`() {
        val insta = "com.instagram.android"
        val policy = AppPolicy(mapOf(insta to AppRule.Allowed, "com.example.maps" to AppRule.Allowed))
        val session = (FocusSession.initial().startFocus(policy, ExitDelay.ONE_DAY) as FocusResult.Success).session
        val catalogue = listOf(
            AppCatalogueEntry(InstalledApp(insta, "Instagram"), AppRule.entries, essential = false),
            AppCatalogueEntry(InstalledApp("com.example.maps", "Maps"), listOf(AppRule.Allowed, AppRule.Blocked), essential = false),
        )
        val form = MakeStricterForm.create(
            catalogue,
            session,
            ExitDelay.PRESETS,
            ruleEdits = mapOf(insta to AppRule.ShortVideoBlocked),
            delayEdit = ExitDelay.SEVEN_DAYS,
        )
        assertEquals(
            listOf("Exit delay: 24 hours → 7 days", "Instagram: Allowed → No short videos"),
            LauncherLabels.stricterChanges(form),
        )
    }

    @Test
    fun `rejected tighten explains why`() {
        assertEquals(
            "Not applied: this would loosen the exit delay, com.game.",
            LauncherLabels.tightenRejected(
                FocusError.NotStricter(
                    listOf(
                        Loosening.ExitDelayShortened(ExitDelay.ONE_DAY, ExitDelay.TWELVE_HOURS),
                        Loosening.AppRuleLoosened("com.game", AppRule.Blocked, AppRule.Allowed),
                    ),
                ),
            ),
        )
        assertEquals(
            "Focus mode is off, so there is nothing to make stricter.",
            LauncherLabels.tightenRejected(FocusError.InvalidTransition(FocusCommand.Tighten, FocusState.Unlocked)),
        )
    }
}
