package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.ShortVideoApps
import dev.phonefocus.domain.shortvideo.NodeMatcher
import dev.phonefocus.domain.shortvideo.RuleBasedDetector
import dev.phonefocus.domain.shortvideo.ShortVideoDetectors
import dev.phonefocus.domain.shortvideo.ShortVideoRule
import dev.phonefocus.domain.shortvideo.SimpleScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShortVideoBlockingTest {
    private val instagram = ShortVideoApps.INSTAGRAM
    private val youtube = ShortVideoApps.YOUTUBE
    private val facebook = ShortVideoApps.FACEBOOK
    private val whatsapp = "com.whatsapp"

    private val policy = AppPolicy(
        mapOf(
            instagram to AppRule.ShortVideoBlocked,
            youtube to AppRule.Allowed,
            facebook to AppRule.Blocked,
            whatsapp to AppRule.Allowed,
        ),
    )

    private fun focused(policy: AppPolicy = this.policy): FocusSession =
        (FocusSession.initial().startFocus(policy, ExitDelay.ONE_DAY) as FocusResult.Success).session

    private fun exitPending(): FocusSession =
        (focused().requestExit(Instant.parse("2026-01-01T00:00:00Z")) as FocusResult.Success).session

    private fun context(session: FocusSession = focused(), essential: Set<String> = emptySet()) =
        AppBlockingContext(
            session,
            EssentialApps(essential),
            systemPackages = emptySet(),
            launchablePackages = setOf(instagram, youtube, facebook, whatsapp),
        )

    private val reelsScreen = SimpleScreenNode(children = listOf(SimpleScreenNode(viewId = "$instagram:id/clips_viewer_view_pager")))
    private val feedScreen = SimpleScreenNode(children = listOf(SimpleScreenNode(viewId = "$instagram:id/row_feed_photo_imageview")))
    private val shortsScreen = SimpleScreenNode(children = listOf(SimpleScreenNode(viewId = "$youtube:id/reel_recycler")))

    private fun evaluate() = EvaluateShortVideoScreen(debounceMillis = 500, maxBacks = 2)

    // --- which apps are inspected ---------------------------------------------------------

    @Test
    fun `only No-short-videos apps are inspected, while focused or exit pending`() {
        assertEquals(setOf(instagram), context().shortVideoBlockedPackages)
        assertEquals(setOf(instagram), context(exitPending()).shortVideoBlockedPackages)
        assertEquals(emptySet<String>(), context(FocusSession.initial()).shortVideoBlockedPackages)
    }

    @Test
    fun `essential apps are never inspected`() {
        assertEquals(emptySet<String>(), context(essential = setOf(instagram)).shortVideoBlockedPackages)
        assertEquals(ForegroundVerdict.Allowed, context(essential = setOf(instagram)).verdict(instagram))
    }

    @Test
    fun `inspects needs a context and a detector`() {
        val evaluate = evaluate()
        assertFalse(evaluate.inspects(null, instagram))
        assertTrue(evaluate.inspects(context(), instagram))
        assertFalse(evaluate.inspects(context(), youtube))
        val noDetectors = EvaluateShortVideoScreen(ShortVideoDetectors(emptyMap()))
        assertFalse(noDetectors.inspects(context(), instagram))
    }

    // --- decisions ------------------------------------------------------------------------

    @Test
    fun `a short video of a No-short-videos app is left with Back`() {
        val action = evaluate()(context(), instagram, 1_000) { reelsScreen }
        assertTrue(action is ShortVideoAction.GoBack)
        action as ShortVideoAction.GoBack
        assertEquals(instagram, action.packageName)
        assertEquals("Instagram Reels viewer", action.match.rule.name)
    }

    @Test
    fun `the same while an exit is pending`() {
        assertTrue(evaluate()(context(exitPending()), instagram, 1_000) { reelsScreen } is ShortVideoAction.GoBack)
    }

    @Test
    fun `other screens of a No-short-videos app stay`() {
        assertEquals(ShortVideoAction.None, evaluate()(context(), instagram, 1_000) { feedScreen })
    }

    @Test
    fun `Allowed apps keep their short videos, and their screen is not even read`() {
        var read = false
        val action = evaluate()(context(), youtube, 1_000) { read = true; shortsScreen }
        assertEquals(ShortVideoAction.None, action)
        assertFalse(read)
    }

    @Test
    fun `nothing happens while Unlocked or before the state is known`() {
        var read = false
        val root = { read = true; reelsScreen }
        assertEquals(ShortVideoAction.None, evaluate()(context(FocusSession.initial()), instagram, 1_000, root))
        assertEquals(ShortVideoAction.None, evaluate()(null, instagram, 1_000, root))
        assertFalse(read)
    }

    @Test
    fun `Blocked apps are not handled here (the foreground blocker bounces them)`() {
        val anyMatch = ShortVideoDetectors(mapOf(facebook to RuleBasedDetector(listOf(ShortVideoRule("all", NodeMatcher.Selected)))))
        val action = EvaluateShortVideoScreen(anyMatch)(context(), facebook, 1_000) { SimpleScreenNode(isSelected = true) }
        assertEquals(ShortVideoAction.None, action)
        assertEquals(ForegroundVerdict.Blocked, context().verdict(facebook))
    }

    @Test
    fun `an unreadable screen is left alone`() {
        assertEquals(ShortVideoAction.None, evaluate()(context(), instagram, 1_000) { null })
    }

    // --- debounce and escalation ----------------------------------------------------------

    @Test
    fun `Back is not repeated within the debounce, a recheck is asked for instead`() {
        val evaluate = evaluate()
        assertTrue(evaluate(context(), instagram, 1_000) { reelsScreen } is ShortVideoAction.GoBack)
        assertEquals(ShortVideoAction.RecheckIn(300), evaluate(context(), instagram, 1_200) { reelsScreen })
        assertTrue(evaluate(context(), instagram, 1_500) { reelsScreen } is ShortVideoAction.GoBack)
    }

    @Test
    fun `when Back keeps failing, the next action returns to the desktop`() {
        val evaluate = evaluate()
        assertTrue(evaluate(context(), instagram, 1_000) { reelsScreen } is ShortVideoAction.GoBack)
        assertTrue(evaluate(context(), instagram, 1_500) { reelsScreen } is ShortVideoAction.GoBack)
        assertTrue(evaluate(context(), instagram, 2_000) { reelsScreen } is ShortVideoAction.ReturnToDesktop)
        // And the count starts over.
        assertTrue(evaluate(context(), instagram, 2_500) { reelsScreen } is ShortVideoAction.GoBack)
    }

    @Test
    fun `a clear screen in between resets the Back count`() {
        val evaluate = evaluate()
        assertTrue(evaluate(context(), instagram, 1_000) { reelsScreen } is ShortVideoAction.GoBack)
        assertTrue(evaluate(context(), instagram, 1_500) { reelsScreen } is ShortVideoAction.GoBack)
        assertEquals(ShortVideoAction.None, evaluate(context(), instagram, 1_700) { feedScreen })
        assertTrue(evaluate(context(), instagram, 2_000) { reelsScreen } is ShortVideoAction.GoBack)
        assertTrue(evaluate(context(), instagram, 2_500) { reelsScreen } is ShortVideoAction.GoBack)
    }

    @Test
    fun `the debounce is per app`() {
        val both = AppPolicy(mapOf(instagram to AppRule.ShortVideoBlocked, youtube to AppRule.ShortVideoBlocked))
        val ctx = context(focused(both))
        val evaluate = evaluate()
        assertTrue(evaluate(ctx, instagram, 1_000) { reelsScreen } is ShortVideoAction.GoBack)
        assertTrue(evaluate(ctx, youtube, 1_100) { shortsScreen } is ShortVideoAction.GoBack)
    }

    // --- debug dump -----------------------------------------------------------------------

    @Test
    fun `the tree dump shows ids, descriptions and flags, indented by depth`() {
        val tree = SimpleScreenNode(
            className = "android.widget.FrameLayout",
            children = listOf(
                SimpleScreenNode(
                    viewId = "$instagram:id/clips_tab",
                    contentDescription = "Reels",
                    className = "android.widget.ImageView",
                    isSelected = true,
                ),
                SimpleScreenNode(className = "android.view.View", isVisibleToUser = false),
            ),
        )
        assertEquals(
            listOf(
                "FrameLayout",
                "  ImageView id=$instagram:id/clips_tab desc=\"Reels\" [selected]",
                "  View [hidden]",
            ),
            ScreenTreeDump.render(tree),
        )
    }

    @Test
    fun `the tree dump is bounded`() {
        val wide = SimpleScreenNode(children = List(10) { SimpleScreenNode() })
        val lines = ScreenTreeDump.render(wide, maxNodes = 3)
        assertEquals(4, lines.size)
        assertTrue(lines.last().startsWith("…"))
    }
}
