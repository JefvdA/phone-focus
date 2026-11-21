package dev.phonefocus.domain

import dev.phonefocus.domain.AppRule.Allowed
import dev.phonefocus.domain.AppRule.Blocked
import dev.phonefocus.domain.AppRule.ShortVideoBlocked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FocusSessionTest {
    private val t0: Instant = Instant.parse("2026-01-01T08:00:00Z")
    private val policy = AppPolicy(
        mapOf(
            "org.phone" to Allowed,
            "com.instagram" to ShortVideoBlocked,
            "com.game" to Blocked,
        ),
    )

    // --- helpers ---------------------------------------------------------------------------

    private fun FocusResult.ok(): FocusSession = when (this) {
        is FocusResult.Success -> session
        is FocusResult.Failure -> throw AssertionError("expected success, got $error")
    }

    private fun FocusResult.err(): FocusError = when (this) {
        is FocusResult.Success -> throw AssertionError("expected failure, got $session")
        is FocusResult.Failure -> error
    }

    private fun unlocked() = FocusSession.initial()
    private fun focused(delay: ExitDelay = ExitDelay.ONE_DAY) = unlocked().startFocus(policy, delay).ok()
    private fun exitPending(delay: ExitDelay = ExitDelay.ONE_DAY) = focused(delay).requestExit(t0).ok()
    private fun unlockAt(session: FocusSession) = (session.state as FocusState.ExitPending).unlockAt

    // --- valid transitions -----------------------------------------------------------------

    @Test
    fun `initial session is Unlocked`() {
        assertEquals(FocusState.Unlocked, unlocked().state)
    }

    @Test
    fun `startFocus moves Unlocked to Focused with the given policy and delay`() {
        val session = unlocked().startFocus(policy, ExitDelay.TWO_DAYS).ok()
        assertEquals(FocusState.Focused, session.state)
        assertEquals(policy, session.policy)
        assertEquals(ExitDelay.TWO_DAYS, session.exitDelay)
    }

    @Test
    fun `requestExit moves Focused to ExitPending with unlockAt = now + delay`() {
        val session = focused(ExitDelay.TWELVE_HOURS).requestExit(t0).ok()
        assertEquals(FocusState.ExitPending(t0.plus(Duration.ofHours(12))), session.state)
    }

    @Test
    fun `requestExit accepts an injected clock`() {
        val session = focused(ExitDelay.ONE_DAY).requestExit(Clock { t0 }).ok()
        assertEquals(t0.plus(Duration.ofHours(24)), unlockAt(session))
    }

    @Test
    fun `cancelExit moves ExitPending back to Focused`() {
        val session = exitPending().cancelExit().ok()
        assertEquals(FocusState.Focused, session.state)
        assertEquals(policy, session.policy)
    }

    @Test
    fun `tick at unlockAt moves ExitPending to Unlocked`() {
        val pending = exitPending()
        assertEquals(FocusState.Unlocked, pending.tick(unlockAt(pending)).state)
    }

    @Test
    fun `tick after unlockAt moves ExitPending to Unlocked`() {
        val pending = exitPending()
        assertEquals(FocusState.Unlocked, pending.tick(unlockAt(pending).plusSeconds(1)).state)
    }

    @Test
    fun `tick accepts an injected clock`() {
        val pending = exitPending()
        assertEquals(FocusState.Unlocked, pending.tick(Clock { unlockAt(pending) }).state)
    }

    @Test
    fun `focus can be started again after unlocking`() {
        val pending = exitPending()
        val again = pending.tick(unlockAt(pending)).startFocus(policy, ExitDelay.SEVEN_DAYS).ok()
        assertEquals(FocusState.Focused, again.state)
        assertEquals(ExitDelay.SEVEN_DAYS, again.exitDelay)
    }

    @Test
    fun `tighten keeps the Focused state`() {
        val session = focused().tighten(Tightening(exitDelay = ExitDelay.TWO_DAYS)).ok()
        assertEquals(FocusState.Focused, session.state)
        assertEquals(ExitDelay.TWO_DAYS, session.exitDelay)
    }

    // --- invalid transitions are rejected (no exceptions) ----------------------------------

    @Test
    fun `startFocus is rejected while Focused`() {
        val session = focused()
        assertEquals(
            FocusError.InvalidTransition(FocusCommand.StartFocus, FocusState.Focused),
            session.startFocus(AppPolicy.EMPTY, ExitDelay.TWELVE_HOURS).err(),
        )
    }

    @Test
    fun `startFocus is rejected while ExitPending`() {
        val session = exitPending()
        assertEquals(
            FocusError.InvalidTransition(FocusCommand.StartFocus, session.state),
            session.startFocus(AppPolicy.EMPTY, ExitDelay.TWELVE_HOURS).err(),
        )
    }

    @Test
    fun `requestExit is rejected while Unlocked`() {
        assertEquals(
            FocusError.InvalidTransition(FocusCommand.RequestExit, FocusState.Unlocked),
            unlocked().requestExit(t0).err(),
        )
    }

    @Test
    fun `requestExit is rejected while ExitPending and keeps the running countdown`() {
        val pending = exitPending()
        assertEquals(
            FocusError.InvalidTransition(FocusCommand.RequestExit, pending.state),
            pending.requestExit(t0.plusSeconds(60)).err(),
        )
    }

    @Test
    fun `cancelExit is rejected while Unlocked`() {
        assertEquals(
            FocusError.InvalidTransition(FocusCommand.CancelExit, FocusState.Unlocked),
            unlocked().cancelExit().err(),
        )
    }

    @Test
    fun `cancelExit is rejected while Focused`() {
        assertEquals(
            FocusError.InvalidTransition(FocusCommand.CancelExit, FocusState.Focused),
            focused().cancelExit().err(),
        )
    }

    @Test
    fun `tighten is rejected while Unlocked`() {
        assertEquals(
            FocusError.InvalidTransition(FocusCommand.Tighten, FocusState.Unlocked),
            unlocked().tighten(Tightening(exitDelay = ExitDelay.SEVEN_DAYS)).err(),
        )
    }

    // --- only the countdown ending leaves focus (invariant 3) ------------------------------

    @Test
    fun `tick before unlockAt keeps ExitPending`() {
        val pending = exitPending()
        assertSame(pending, pending.tick(unlockAt(pending).minusMillis(1)))
    }

    @Test
    fun `tick while Focused never leaves focus`() {
        val session = focused()
        assertSame(session, session.tick(t0.plus(Duration.ofDays(3650))))
    }

    @Test
    fun `cancelExit does not leave focus`() {
        assertTrue(exitPending().cancelExit().ok().state.isFocusActive)
    }

    // --- stays Unlocked after unlocking (invariant 4) --------------------------------------

    @Test
    fun `after unlocking the session stays Unlocked on later ticks`() {
        val pending = exitPending()
        val unlocked = pending.tick(unlockAt(pending))
        val later = unlocked.tick(unlockAt(pending).plus(Duration.ofDays(30)))
        assertEquals(FocusState.Unlocked, later.state)
        assertSame(unlocked, later)
    }

    @Test
    fun `tick while Unlocked is a no-op`() {
        val session = unlocked()
        assertSame(session, session.tick(t0))
    }

    // --- tighten: only stricter (invariant 1) ----------------------------------------------

    @Test
    fun `tighten rejects a shorter delay`() {
        val session = focused(ExitDelay.TWO_DAYS)
        assertEquals(
            FocusError.NotStricter(listOf(Loosening.ExitDelayShortened(ExitDelay.TWO_DAYS, ExitDelay.ONE_DAY))),
            session.tighten(Tightening(exitDelay = ExitDelay.ONE_DAY)).err(),
        )
    }

    @Test
    fun `tighten rejects Blocked to Allowed`() {
        assertEquals(
            FocusError.NotStricter(listOf(Loosening.AppRuleLoosened("com.game", Blocked, Allowed))),
            focused().tighten(Tightening(rules = mapOf("com.game" to Allowed))).err(),
        )
    }

    @Test
    fun `tighten rejects Blocked to ShortVideoBlocked`() {
        assertEquals(
            FocusError.NotStricter(listOf(Loosening.AppRuleLoosened("com.game", Blocked, ShortVideoBlocked))),
            focused().tighten(Tightening(rules = mapOf("com.game" to ShortVideoBlocked))).err(),
        )
    }

    @Test
    fun `tighten rejects ShortVideoBlocked to Allowed`() {
        assertEquals(
            FocusError.NotStricter(listOf(Loosening.AppRuleLoosened("com.instagram", ShortVideoBlocked, Allowed))),
            focused().tighten(Tightening(rules = mapOf("com.instagram" to Allowed))).err(),
        )
    }

    @Test
    fun `tighten rejects allowing an unlisted app because it is Blocked by default`() {
        assertEquals(
            FocusError.NotStricter(listOf(Loosening.AppRuleLoosened("com.new", Blocked, Allowed))),
            focused().tighten(Tightening(rules = mapOf("com.new" to Allowed))).err(),
        )
    }

    @Test
    fun `tighten rejects loosenings during ExitPending too`() {
        val result = exitPending(ExitDelay.TWO_DAYS).tighten(
            Tightening(rules = mapOf("com.game" to Allowed), exitDelay = ExitDelay.TWELVE_HOURS),
        )
        assertEquals(
            FocusError.NotStricter(
                listOf(
                    Loosening.ExitDelayShortened(ExitDelay.TWO_DAYS, ExitDelay.TWELVE_HOURS),
                    Loosening.AppRuleLoosened("com.game", Blocked, Allowed),
                ),
            ),
            result.err(),
        )
    }

    @Test
    fun `tighten is all or nothing - a loosening rejects the stricter changes in the same request`() {
        val result = focused().tighten(
            Tightening(rules = mapOf("org.phone" to Blocked, "com.instagram" to Allowed)),
        )
        assertEquals(
            FocusError.NotStricter(listOf(Loosening.AppRuleLoosened("com.instagram", ShortVideoBlocked, Allowed))),
            result.err(),
        )
    }

    @Test
    fun `tighten accepts a longer delay`() {
        assertEquals(
            ExitDelay.SEVEN_DAYS,
            focused(ExitDelay.ONE_DAY).tighten(Tightening(exitDelay = ExitDelay.SEVEN_DAYS)).ok().exitDelay,
        )
    }

    @Test
    fun `tighten accepts Allowed to ShortVideoBlocked, ShortVideoBlocked to Blocked and Allowed to Blocked`() {
        val session = focused().tighten(
            Tightening(rules = mapOf("org.phone" to ShortVideoBlocked, "com.instagram" to Blocked)),
        ).ok()
        assertEquals(ShortVideoBlocked, session.policy.ruleFor("org.phone"))
        assertEquals(Blocked, session.policy.ruleFor("com.instagram"))

        val direct = focused().tighten(Tightening(rules = mapOf("org.phone" to Blocked))).ok()
        assertEquals(Blocked, direct.policy.ruleFor("org.phone"))
    }

    @Test
    fun `tighten accepts unchanged values as a no-op`() {
        val session = focused(ExitDelay.ONE_DAY)
        val same = session.tighten(
            Tightening(rules = mapOf("org.phone" to Allowed, "com.new" to Blocked), exitDelay = ExitDelay.ONE_DAY),
        ).ok()
        assertEquals(FocusState.Focused, same.state)
        assertEquals(ExitDelay.ONE_DAY, same.exitDelay)
        assertTrue(same.isLaunchAllowed("org.phone"))
        assertFalse(same.isLaunchAllowed("com.new"))
    }

    // --- running countdown keeps unlockAt (invariant 2) ------------------------------------

    @Test
    fun `tighten during ExitPending keeps the original unlockAt`() {
        val pending = exitPending(ExitDelay.ONE_DAY)
        val originalUnlockAt = unlockAt(pending)

        val tightened = pending.tighten(
            Tightening(rules = mapOf("org.phone" to Blocked), exitDelay = ExitDelay.SEVEN_DAYS),
        ).ok()

        assertEquals(FocusState.ExitPending(originalUnlockAt), tightened.state)
        assertEquals(ExitDelay.SEVEN_DAYS, tightened.exitDelay)
        assertEquals(FocusState.Unlocked, tightened.tick(originalUnlockAt).state)
    }

    @Test
    fun `cancelExit then requestExit uses the current, longer delay`() {
        val pending = exitPending(ExitDelay.ONE_DAY)
        val tightened = pending.tighten(Tightening(exitDelay = ExitDelay.SEVEN_DAYS)).ok()
        val later = t0.plus(Duration.ofHours(5))

        val restarted = tightened.cancelExit().ok().requestExit(later).ok()

        assertEquals(FocusState.ExitPending(later.plus(Duration.ofDays(7))), restarted.state)
    }

    @Test
    fun `cancelExit then requestExit without changes restarts the countdown from now`() {
        val later = t0.plus(Duration.ofHours(5))
        val restarted = exitPending(ExitDelay.ONE_DAY).cancelExit().ok().requestExit(later).ok()
        assertEquals(later.plus(Duration.ofHours(24)), unlockAt(restarted))
    }

    // --- queries ---------------------------------------------------------------------------

    @Test
    fun `isLaunchAllowed during focus follows the policy`() {
        for (session in listOf(focused(), exitPending())) {
            assertTrue(session.isLaunchAllowed("org.phone"))
            assertTrue(session.isLaunchAllowed("com.instagram"))
            assertFalse(session.isLaunchAllowed("com.game"))
        }
    }

    @Test
    fun `unknown package during focus is treated as Blocked`() {
        val session = focused()
        assertEquals(Blocked, session.policy.ruleFor("com.unknown"))
        assertFalse(session.isLaunchAllowed("com.unknown"))
        assertTrue(session.isShortVideoBlocked("com.unknown"))
        assertFalse("com.unknown" in session.allowedApps())
    }

    @Test
    fun `everything may launch and nothing is short-video blocked while Unlocked`() {
        val session = FocusSession.initial(policy = policy)
        assertTrue(session.isLaunchAllowed("com.game"))
        assertTrue(session.isLaunchAllowed("com.unknown"))
        assertFalse(session.isShortVideoBlocked("com.instagram"))
    }

    @Test
    fun `isShortVideoBlocked during focus is true for ShortVideoBlocked and Blocked apps`() {
        val session = focused()
        assertFalse(session.isShortVideoBlocked("org.phone"))
        assertTrue(session.isShortVideoBlocked("com.instagram"))
        assertTrue(session.isShortVideoBlocked("com.game"))
    }

    @Test
    fun `allowedApps during focus lists the launchable apps`() {
        assertEquals(setOf("org.phone", "com.instagram"), focused().allowedApps())
        assertEquals(setOf("org.phone", "com.instagram"), exitPending().allowedApps())
    }

    @Test
    fun `allowedApps reflects tightening`() {
        val session = focused().tighten(Tightening(rules = mapOf("com.instagram" to Blocked))).ok()
        assertEquals(setOf("org.phone"), session.allowedApps())
    }

    @Test
    fun `allowedApps is empty while Unlocked`() {
        assertEquals(emptySet<String>(), FocusSession.initial(policy = policy).allowedApps())
    }

    @Test
    fun `queries after unlocking no longer enforce the policy`() {
        val pending = exitPending()
        val unlocked = pending.tick(unlockAt(pending))
        assertTrue(unlocked.isLaunchAllowed("com.game"))
        assertFalse(unlocked.isShortVideoBlocked("com.instagram"))
    }

    // --- restore ---------------------------------------------------------------------------

    @Test
    fun `restore rehydrates every state with its policy and delay`() {
        val states = listOf(FocusState.Unlocked, FocusState.Focused, FocusState.ExitPending(t0))
        for (state in states) {
            val restored = FocusSession.restore(state, policy, ExitDelay.TWO_DAYS)
            assertEquals(state, restored.state)
            assertEquals(policy, restored.policy)
            assertEquals(ExitDelay.TWO_DAYS, restored.exitDelay)
        }
    }

    @Test
    fun `restore equals the session it was saved from`() {
        val pending = exitPending(ExitDelay.SEVEN_DAYS)
        assertEquals(pending, FocusSession.restore(pending.state, pending.policy, pending.exitDelay))
    }

    @Test
    fun `a restored ExitPending session keeps its unlockAt and invariants`() {
        val restored = FocusSession.restore(FocusState.ExitPending(t0), policy, ExitDelay.ONE_DAY)
        assertSame(restored, restored.tick(t0.minusMillis(1)))
        assertEquals(FocusState.Unlocked, restored.tick(t0).state)
        assertTrue(restored.startFocus(policy, ExitDelay.ONE_DAY).err() is FocusError.InvalidTransition)
        assertTrue(restored.tighten(Tightening(exitDelay = ExitDelay.TWELVE_HOURS)).err() is FocusError.NotStricter)
        assertEquals(FocusState.ExitPending(t0), restored.tighten(Tightening(exitDelay = ExitDelay.TWO_DAYS)).ok().state)
    }

    @Test
    fun `a restored Focused session can only leave focus through the countdown`() {
        val restored = FocusSession.restore(FocusState.Focused, policy, ExitDelay.TWELVE_HOURS)
        assertSame(restored, restored.tick(t0.plus(Duration.ofDays(365))))
        assertFalse(restored.isLaunchAllowed("com.game"))
        assertEquals(
            FocusState.ExitPending(t0.plus(Duration.ofHours(12))),
            restored.requestExit(t0).ok().state,
        )
    }
}
