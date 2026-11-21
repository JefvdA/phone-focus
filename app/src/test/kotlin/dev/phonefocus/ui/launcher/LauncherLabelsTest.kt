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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class LauncherLabelsTest {
    @Test
    fun `focus button shows Focus, or the remaining time while exit is pending`() {
        assertEquals("Focus", LauncherLabels.focusButton(null))
        assertEquals("Exit 23:42", LauncherLabels.focusButton(Duration.ofHours(23).plusMinutes(41).plusSeconds(7)))
        assertEquals("Exit 24:00", LauncherLabels.focusButton(Duration.ofHours(24)))
    }

    @Test
    fun `emergency escape confirmation uses the session's actual delay`() {
        assertEquals(
            "The phone unlocks in 24 hours. Continue?\n\nFocus mode stays on until then. You can cancel the exit at any time.",
            LauncherLabels.emergencyConfirmation(ExitDelay.ONE_DAY),
        )
        assertTrue(LauncherLabels.emergencyConfirmation(ExitDelay.SEVEN_DAYS).startsWith("The phone unlocks in 7 days."))
        assertTrue(LauncherLabels.emergencyConfirmation(ExitDelay(Duration.ofMinutes(1))).startsWith("The phone unlocks in 1 minute."))
    }

    @Test
    fun `exit countdown shows hh mm ss, rounded up`() {
        assertEquals("23:41:08", LauncherLabels.exitCountdown(Duration.ofHours(23).plusMinutes(41).plusSeconds(7).plusMillis(200)))
        assertEquals("00:00:00", LauncherLabels.exitCountdown(Duration.ZERO))
    }

    @Test
    fun `rejected exit commands explain why`() {
        assertEquals(
            "Focus mode has already ended.",
            LauncherLabels.exitRejected(FocusError.InvalidTransition(FocusCommand.CancelExit, FocusState.Unlocked)),
        )
        assertEquals(
            "No exit is requested.",
            LauncherLabels.exitRejected(FocusError.InvalidTransition(FocusCommand.CancelExit, FocusState.Focused)),
        )
        assertEquals(
            "Exit is already requested.",
            LauncherLabels.exitRejected(
                FocusError.InvalidTransition(FocusCommand.RequestExit, FocusState.ExitPending(java.time.Instant.EPOCH)),
            ),
        )
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
