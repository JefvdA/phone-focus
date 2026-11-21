package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.AppRuleOptions
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusCommand
import dev.phonefocus.domain.FocusError
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class StartFocusFromSetupTest {
    private val repo = FakeRepository()
    private val commands = FocusCommands(repo, FakeDeviceClock(Instant.parse("2026-01-01T08:00:00Z")), FakeScheduler())
    private val prerequisites = FakePrerequisites()
    private val start = StartFocusFromSetup(commands, prerequisites)

    private val maps = AppCatalogueEntry(
        InstalledApp("org.maps", "Maps"),
        AppRuleOptions.forPackage("org.maps", EssentialApps.NONE),
        essential = false,
    )
    private val form = FocusSetupForm.create(
        listOf(maps),
        FocusSession.initial(),
        ExitDelay.PRESETS,
        ruleEdits = mapOf("org.maps" to AppRule.Allowed),
        delayEdit = ExitDelay.TWO_DAYS,
    )

    private val session get() = repo.state.value.session

    @Test
    fun `starts focus with the form's policy and delay`() = runBlocking {
        assertEquals(SetupStartResult.Started, start(form))
        assertEquals(FocusState.Focused, session.state)
        assertEquals(AppPolicy(mapOf("org.maps" to AppRule.Allowed)), session.policy)
        assertEquals(ExitDelay.TWO_DAYS, session.exitDelay)
    }

    @Test
    fun `the started policy and delay are the defaults of the next setup`() = runBlocking {
        start(form)
        // Unlock again without touching the policy (as the countdown ending does).
        repo.state.value = FocusRecord(FocusSession.restore(FocusState.Unlocked, session.policy, session.exitDelay))
        val next = FocusSetupForm.create(listOf(maps), session, ExitDelay.PRESETS)
        assertEquals(form.apps, next.apps)
        assertEquals(ExitDelay.TWO_DAYS, next.exitDelay)
    }

    @Test
    fun `a missing prerequisite blocks the start and changes nothing`() = runBlocking {
        prerequisites.statuses[StartPrerequisite.DefaultHome] = PrerequisiteStatus.Missing
        assertEquals(SetupStartResult.PrerequisitesMissing(listOf(StartPrerequisite.DefaultHome)), start(form))
        assertEquals(FocusState.Unlocked, session.state)
        assertEquals(0, repo.writes)
    }

    @Test
    fun `features this build does not have yet do not block the start`() = runBlocking {
        StartPrerequisite.entries.forEach { prerequisites.statuses[it] = PrerequisiteStatus.NotAvailable }
        assertEquals(SetupStartResult.Started, start(form))
    }

    @Test
    fun `starting while focused is rejected and keeps the running policy`() = runBlocking {
        commands.startFocus(AppPolicy.EMPTY, ExitDelay.SEVEN_DAYS)
        val result = start(form)
        assertEquals(
            SetupStartResult.Rejected(FocusError.InvalidTransition(FocusCommand.StartFocus, FocusState.Focused)),
            result,
        )
        assertEquals(AppPolicy.EMPTY, session.policy)
        assertEquals(ExitDelay.SEVEN_DAYS, session.exitDelay)
    }

    @Test
    fun `missing lists only Missing prerequisites in declaration order`() = runBlocking {
        prerequisites.statuses[StartPrerequisite.AccessibilityService] = PrerequisiteStatus.Missing
        prerequisites.statuses[StartPrerequisite.DefaultHome] = PrerequisiteStatus.Missing
        assertEquals(StartPrerequisite.entries.toList(), prerequisites.missing())
        prerequisites.statuses[StartPrerequisite.DefaultHome] = PrerequisiteStatus.Met
        assertEquals(listOf(StartPrerequisite.AccessibilityService), prerequisites.missing())
    }
}
