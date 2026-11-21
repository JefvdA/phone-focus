package dev.phonefocus.domain.shortvideo

import dev.phonefocus.domain.ShortVideoApps
import dev.phonefocus.domain.shortvideo.NodeMatcher.AllOf
import dev.phonefocus.domain.shortvideo.NodeMatcher.ContentDescription
import dev.phonefocus.domain.shortvideo.NodeMatcher.Selected
import dev.phonefocus.domain.shortvideo.NodeMatcher.ViewId
import dev.phonefocus.domain.shortvideo.NodeMatcher.ViewIdPrefix

/**
 * The short-video detection rules of every app in [ShortVideoApps] (#12), in one place.
 *
 * There is no official API: these match view ids and content descriptions of the apps' own
 * UI, so they break when an app changes it. To update them, use the debug screen's screen tree
 * dump (debug builds) on the short-video screen and add or change the identifiers here.
 *
 * Rules must only match while a short-video feed or player is actually on screen: a bottom
 * navigation tab only counts when it is [Selected], and only visible nodes are looked at (so
 * a Reels page kept off screen by a view pager does not count).
 *
 * Status (identifiers from public knowledge of the apps, to be verified on the device):
 * - Instagram: the Reels viewer (Reels tab, a Reel opened from the feed, a profile or a DM)
 *   and the selected Reels tab.
 * - YouTube: the Shorts player and the selected Shorts tab.
 * - Facebook, Snapchat: no rules yet (their ids are obfuscated); short videos are not blocked.
 */
object ShortVideoRules {
    private const val IG = ShortVideoApps.INSTAGRAM + ":id/"
    private const val YT = ShortVideoApps.YOUTUBE + ":id/"

    val BY_PACKAGE: Map<String, List<ShortVideoRule>> = mapOf(
        ShortVideoApps.INSTAGRAM to listOf(
            // The full-screen Reels viewer ("clips" internally): the Reels tab and any Reel
            // opened elsewhere (feed, profile grid, a Reel shared in a DM) use it.
            ShortVideoRule("Instagram Reels viewer", ViewIdPrefix(IG + "clips_viewer_")),
            ShortVideoRule("Instagram Reels tab", AllOf(ViewId(IG + "clips_tab"), Selected)),
        ),
        ShortVideoApps.YOUTUBE to listOf(
            // The Shorts player ("reels" internally), from the Shorts tab, a shelf or a link.
            ShortVideoRule("YouTube Shorts player", ViewId(YT + "reel_recycler")),
            ShortVideoRule("YouTube Shorts player", ViewId(YT + "reel_player_page_container")),
            ShortVideoRule("YouTube Shorts player", ViewId(YT + "reel_watch_player")),
            ShortVideoRule("YouTube Shorts tab", AllOf(ContentDescription("Shorts"), Selected)),
        ),
        ShortVideoApps.FACEBOOK to emptyList(),
        ShortVideoApps.SNAPCHAT to emptyList(),
    )
}
