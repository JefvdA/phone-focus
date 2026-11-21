package dev.phonefocus.domain.shortvideo

import dev.phonefocus.domain.ShortVideoApps
import dev.phonefocus.domain.shortvideo.NodeMatcher.AllOf
import dev.phonefocus.domain.shortvideo.NodeMatcher.ClassName
import dev.phonefocus.domain.shortvideo.NodeMatcher.ContentDescription
import dev.phonefocus.domain.shortvideo.NodeMatcher.Selected
import dev.phonefocus.domain.shortvideo.NodeMatcher.ViewId
import dev.phonefocus.domain.shortvideo.NodeMatcher.ViewIdPrefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeMatcherTest {
    private val node = SimpleScreenNode(
        viewId = "com.example:id/clips_viewer_pager",
        contentDescription = "  Reels ",
        className = "android.widget.FrameLayout",
        isSelected = true,
    )

    @Test
    fun `view id matches exactly or by prefix`() {
        assertTrue(ViewId("com.example:id/clips_viewer_pager").matches(node))
        assertFalse(ViewId("com.example:id/clips_viewer").matches(node))
        assertTrue(ViewIdPrefix("com.example:id/clips_viewer_").matches(node))
        assertFalse(ViewIdPrefix("com.other:id/clips_viewer_").matches(node))
        assertFalse(ViewIdPrefix("com.example:id/").matches(SimpleScreenNode()))
    }

    @Test
    fun `content description ignores case and surrounding blanks, but not partial words`() {
        assertTrue(ContentDescription("reels").matches(node))
        assertFalse(ContentDescription("Reel").matches(node))
        assertFalse(ContentDescription("Reels").matches(SimpleScreenNode()))
    }

    @Test
    fun `class name, selected and all-of`() {
        assertTrue(ClassName("android.widget.FrameLayout").matches(node))
        assertTrue(Selected.matches(node))
        assertFalse(Selected.matches(node.copy(isSelected = false)))
        assertTrue(AllOf(ContentDescription("Reels"), Selected).matches(node))
        assertFalse(AllOf(ContentDescription("Reels"), Selected).matches(node.copy(isSelected = false)))
    }
}

class ScreenTreeTest {
    @Test
    fun `finds visible nodes depth first and skips invisible subtrees`() {
        val hiddenTarget = SimpleScreenNode(viewId = "target")
        val tree = SimpleScreenNode(
            children = listOf(
                SimpleScreenNode(isVisibleToUser = false, children = listOf(hiddenTarget)),
                SimpleScreenNode(children = listOf(SimpleScreenNode(viewId = "target", contentDescription = "visible"))),
            ),
        )
        assertEquals("visible", ScreenTree.firstVisible(tree) { it.viewId == "target" }?.contentDescription)
        assertNull(ScreenTree.firstVisible(SimpleScreenNode(isVisibleToUser = false, children = listOf(hiddenTarget))) { it.viewId == "target" })
    }

    @Test
    fun `looks at no more than the node budget`() {
        val wide = SimpleScreenNode(children = List(10) { SimpleScreenNode() } + SimpleScreenNode(viewId = "target"))
        assertNull(ScreenTree.firstVisible(wide, maxNodes = 5) { it.viewId == "target" })
        assertEquals("target", ScreenTree.firstVisible(wide, maxNodes = 12) { it.viewId == "target" }?.viewId)
    }
}

/** The rules of [ShortVideoRules] against simplified screens of the real apps. */
class ShortVideoRulesTest {
    private val detectors = ShortVideoDetectors.DEFAULT
    private val ig = ShortVideoApps.INSTAGRAM
    private val yt = ShortVideoApps.YOUTUBE

    private fun igId(name: String) = "$ig:id/$name"
    private fun ytId(name: String) = "$yt:id/$name"

    /** Instagram's bottom navigation, with [selected] (a view id) as the current tab. */
    private fun igTabs(selected: String?) = SimpleScreenNode(
        viewId = igId("tab_bar"),
        children = listOf("feed_tab" to "Home", "search_tab" to "Search", "clips_tab" to "Reels", "profile_tab" to "Profile")
            .map { (id, desc) -> SimpleScreenNode(viewId = igId(id), contentDescription = desc, isSelected = id == selected) },
    )

    private fun igScreen(vararg content: SimpleScreenNode, tab: String? = "feed_tab") =
        SimpleScreenNode(viewId = "android:id/content", children = content.toList() + listOfNotNull(tab?.let(::igTabs)))

    private fun igReelsViewer() = SimpleScreenNode(
        viewId = igId("clips_viewer_view_pager"),
        className = "androidx.viewpager.widget.ViewPager",
        children = listOf(SimpleScreenNode(viewId = igId("clips_video_container"))),
    )

    @Test
    fun `every short-video app has a detector, and only those`() {
        assertEquals(ShortVideoApps.PACKAGES, ShortVideoRules.BY_PACKAGE.keys)
        assertEquals(ShortVideoApps.PACKAGES, detectors.packages)
    }

    @Test
    fun `Instagram feed, DMs and profile are not short videos`() {
        val feed = igScreen(SimpleScreenNode(viewId = igId("row_feed_photo_imageview")), tab = "feed_tab")
        val dmThread = igScreen(
            SimpleScreenNode(
                viewId = igId("message_list"),
                // A Reel shared in a DM is only a preview bubble until it is opened.
                children = listOf(SimpleScreenNode(viewId = igId("clips_share_preview"), contentDescription = "Reel by someone")),
            ),
            tab = null,
        )
        val profile = igScreen(SimpleScreenNode(viewId = igId("profile_tabs_container")), tab = "profile_tab")
        listOf(feed, dmThread, profile).forEach { assertNull(detectors.detect(ig, it)) }
    }

    @Test
    fun `Instagram Reels tab is a short video`() {
        val reelsTab = igScreen(igReelsViewer(), tab = "clips_tab")
        assertEquals("Instagram Reels viewer", detectors.detect(ig, reelsTab)?.rule?.name)
        // Even before the viewer is laid out, the selected tab gives it away.
        assertEquals("Instagram Reels tab", detectors.detect(ig, igScreen(tab = "clips_tab"))?.rule?.name)
    }

    @Test
    fun `a Reel opened from a DM is a short video`() {
        val openedFromDm = igScreen(igReelsViewer(), tab = null)
        assertEquals("Instagram Reels viewer", detectors.detect(ig, openedFromDm)?.rule?.name)
    }

    @Test
    fun `an off-screen Reels page kept by the pager is not a short video`() {
        val feedWithHiddenReels = igScreen(
            SimpleScreenNode(viewId = igId("row_feed_photo_imageview")),
            igReelsViewer().copy(isVisibleToUser = false),
            tab = "feed_tab",
        )
        assertNull(detectors.detect(ig, feedWithHiddenReels))
    }

    /** YouTube's bottom navigation, with the tab described [selected] as the current one. */
    private fun ytTabs(selected: String) = SimpleScreenNode(
        viewId = ytId("pivot_bar"),
        children = listOf("Home", "Shorts", "Subscriptions", "You")
            .map { SimpleScreenNode(contentDescription = it, isSelected = it == selected) },
    )

    @Test
    fun `YouTube home, with a Shorts shelf, and a normal video are not short videos`() {
        val home = SimpleScreenNode(
            children = listOf(
                SimpleScreenNode(
                    viewId = ytId("results"),
                    children = listOf(SimpleScreenNode(contentDescription = "Shorts"), SimpleScreenNode(viewId = ytId("thumbnail"))),
                ),
                ytTabs("Home"),
            ),
        )
        val watch = SimpleScreenNode(children = listOf(SimpleScreenNode(viewId = ytId("watch_player"))))
        assertNull(detectors.detect(yt, home))
        assertNull(detectors.detect(yt, watch))
    }

    @Test
    fun `YouTube Shorts player and tab are short videos`() {
        val player = SimpleScreenNode(children = listOf(SimpleScreenNode(viewId = ytId("reel_recycler"))))
        assertEquals("YouTube Shorts player", detectors.detect(yt, player)?.rule?.name)
        val linkedShort = SimpleScreenNode(children = listOf(SimpleScreenNode(viewId = ytId("reel_player_page_container"))))
        assertEquals("YouTube Shorts player", detectors.detect(yt, linkedShort)?.rule?.name)
        assertEquals("YouTube Shorts tab", detectors.detect(yt, SimpleScreenNode(children = listOf(ytTabs("Shorts"))))?.rule?.name)
    }

    @Test
    fun `apps without rules and unknown apps never match`() {
        val anything = SimpleScreenNode(children = listOf(igReelsViewer(), SimpleScreenNode(viewId = ytId("reel_recycler"))))
        assertNull(detectors.detect(ShortVideoApps.FACEBOOK, anything))
        assertNull(detectors.detect(ShortVideoApps.SNAPCHAT, anything))
        assertNull(detectors.detect("com.example.other", anything))
    }

    @Test
    fun `rules are per app`() {
        // Instagram's ids on a YouTube screen (or vice versa) mean nothing.
        assertNull(detectors.detect(yt, igScreen(igReelsViewer())))
        assertNull(detectors.detect(ig, SimpleScreenNode(children = listOf(SimpleScreenNode(viewId = ytId("reel_recycler"))))))
    }

    @Test
    fun `custom detectors can be registered by package`() {
        val custom = ShortVideoDetectors(mapOf("com.example.tok" to RuleBasedDetector(listOf(ShortVideoRule("feed", ViewId("feed"))))))
        assertTrue(custom.detect("com.example.tok", SimpleScreenNode(viewId = "feed")) != null)
        assertNull(RuleBasedDetector(emptyList()).detect(SimpleScreenNode(viewId = "feed")))
    }
}
