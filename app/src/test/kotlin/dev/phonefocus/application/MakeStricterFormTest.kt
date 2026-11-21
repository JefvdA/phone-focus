package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.AppRule.Allowed
import dev.phonefocus.domain.AppRule.Blocked
import dev.phonefocus.domain.AppRule.ShortVideoBlocked
import dev.phonefocus.domain.AppRuleOptions
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusError
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import dev.phonefocus.domain.ShortVideoApps
import dev.phonefocus.domain.Tightening
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MakeStricterFormTest {
    private val settings = "com.android.settings"
    private val maps = "com.example.maps"
    private val game = "com.example.game"
    private val newApp = "com.example.new"
    private val insta = ShortVideoApps.INSTAGRAM
    private val youtube = ShortVideoApps.YOUTUBE
    private val essential = EssentialApps(setOf(settings))

    private val catalogue = listOf(
        InstalledApp(game, "Game"),
        InstalledApp(insta, "Instagram"),
        InstalledApp(maps, "Maps"),
        InstalledApp(newApp, "New app"),
        InstalledApp(settings, "Settings"),
        InstalledApp(youtube, "YouTube"),
    ).map { AppCatalogueEntry(it, AppRuleOptions.forPackage(it.packageName, essential), it.packageName in essential) }

    private val policy = AppPolicy(
        mapOf(maps to Allowed, insta to Allowed, youtube to ShortVideoBlocked, game to Blocked, settings to Blocked),
    )

    private fun FocusResult.ok(): FocusSession = (this as? FocusResult.Success)?.session
        ?: throw AssertionError("expected success, got $this")

    private val focused = FocusSession.initial().startFocus(policy, ExitDelay.ONE_DAY).ok()

    private fun form(
        session: FocusSession = focused,
        ruleEdits: Map<String, AppRule> = emptyMap(),
        delayEdit: ExitDelay? = null,
    ) = MakeStricterForm.create(catalogue, session, ExitDelayOptions.available(debugBuild = true), ruleEdits, delayEdit)

    // --- what is offered -------------------------------------------------------------------

    @Test
    fun `lists only the currently allowed apps, without essential apps, in catalogue order`() {
        assertEquals(listOf(insta, maps, youtube), form().apps.map { it.packageName })
    }

    @Test
    fun `each app offers only its current rule and stricter ones`() {
        val options = form().apps.associate { it.packageName to it.options }
        assertEquals(listOf(Allowed, ShortVideoBlocked, Blocked), options[insta])
        assertEquals(listOf(Allowed, Blocked), options[maps])
        assertEquals(listOf(ShortVideoBlocked, Blocked), options[youtube])
    }

    @Test
    fun `starts with the current rules and delay, so nothing changes`() {
        val form = form()
        assertTrue(form.apps.all { it.rule == it.current && !it.changed })
        assertEquals(ExitDelay.ONE_DAY, form.exitDelay)
        assertFalse(form.hasChanges)
        assertEquals(Tightening(), form.tightening())
    }

    @Test
    fun `delays shorter than the current one are disabled`() {
        val enabled = form().exitDelayOptions.filter { it.enabled }.map { it.delay }
        assertEquals(listOf(ExitDelay.ONE_DAY, ExitDelay.TWO_DAYS, ExitDelay.SEVEN_DAYS), enabled)
    }

    // --- edits -----------------------------------------------------------------------------

    @Test
    fun `stricter edits end up in the tightening, unchanged values do not`() {
        val form = form(ruleEdits = mapOf(insta to ShortVideoBlocked, youtube to Blocked, maps to Allowed), delayEdit = ExitDelay.SEVEN_DAYS)
        assertTrue(form.hasChanges)
        assertEquals(listOf(insta, youtube), form.changedApps.map { it.packageName })
        assertEquals(
            Tightening(rules = mapOf(insta to ShortVideoBlocked, youtube to Blocked), exitDelay = ExitDelay.SEVEN_DAYS),
            form.tightening(),
        )
    }

    @Test
    fun `choosing the current delay is a no-op`() {
        val form = form(delayEdit = ExitDelay.ONE_DAY)
        assertFalse(form.delayChanged)
        assertNull(form.tightening().exitDelay)
    }

    @Test
    fun `looser or unsupported edits are ignored`() {
        val form = form(
            ruleEdits = mapOf(youtube to Allowed, maps to ShortVideoBlocked, game to Allowed, settings to Allowed),
            delayEdit = ExitDelay.TWELVE_HOURS,
        )
        assertFalse(form.hasChanges)
        assertEquals(Tightening(), form.tightening())
    }

    @Test
    fun `an edit that is no longer offered after the session got stricter is dropped`() {
        val stricter = focused.tighten(Tightening(rules = mapOf(insta to Blocked), exitDelay = ExitDelay.TWO_DAYS)).ok()
        val form = form(stricter, ruleEdits = mapOf(insta to ShortVideoBlocked), delayEdit = ExitDelay.ONE_DAY)
        assertEquals(listOf(maps, youtube), form.apps.map { it.packageName })
        assertEquals(ExitDelay.TWO_DAYS, form.exitDelay)
        assertFalse(form.hasChanges)
    }

    @Test
    fun `any tightening the form produces is accepted by the domain`() {
        val form = form(ruleEdits = mapOf(insta to Blocked, maps to Blocked, youtube to Blocked), delayEdit = ExitDelay.SEVEN_DAYS)
        val session = focused.tighten(form.tightening()).ok()
        assertEquals(emptySet<String>(), session.allowedApps())
        assertEquals(ExitDelay.SEVEN_DAYS, session.exitDelay)
    }

    // --- through FocusCommands -------------------------------------------------------------

    private val t0 = Instant.parse("2026-01-01T08:00:00Z")
    private val repo = FakeRepository()
    private val clock = FakeDeviceClock(t0)
    private val scheduler = FakeScheduler()
    private val commands = FocusCommands(repo, clock, scheduler)

    @Test
    fun `applying a longer delay during Exit pending keeps unlockAt and the scheduled unlock`() = runBlocking {
        commands.startFocus(policy, ExitDelay.ONE_DAY).ok()
        commands.requestExit().ok()
        clock.advance(Duration.ofHours(3))
        commands.tick()
        val unlockAt = t0 + Duration.ofDays(1)
        assertEquals(FocusState.ExitPending(unlockAt), repo.state.value.session.state)
        assertEquals(Duration.ofHours(21), scheduler.scheduled)

        val form = MakeStricterForm.create(
            catalogue,
            repo.state.value.session,
            ExitDelay.PRESETS,
            ruleEdits = mapOf(insta to ShortVideoBlocked),
            delayEdit = ExitDelay.SEVEN_DAYS,
        )
        val session = commands.tighten(form.tightening()).ok()

        assertEquals(FocusState.ExitPending(unlockAt), session.state)
        assertEquals(ExitDelay.SEVEN_DAYS, session.exitDelay)
        assertEquals(ShortVideoBlocked, session.policy.ruleFor(insta))
        assertEquals(Duration.ofHours(21), scheduler.scheduled)
    }

    @Test
    fun `bypassing the form, a loosening is still rejected by the domain and changes nothing`() = runBlocking {
        commands.startFocus(policy, ExitDelay.ONE_DAY).ok()
        commands.requestExit().ok()
        val before = repo.state.value
        val calls = scheduler.calls.size

        val result = commands.tighten(
            Tightening(rules = mapOf(insta to Blocked, youtube to Allowed, game to Allowed), exitDelay = ExitDelay.TWELVE_HOURS),
        )

        val error = (result as FocusResult.Failure).error
        assertTrue(error is FocusError.NotStricter)
        assertEquals(3, (error as FocusError.NotStricter).loosenings.size)
        assertEquals(before, repo.state.value)
        assertEquals(calls, scheduler.calls.size)
    }
}
