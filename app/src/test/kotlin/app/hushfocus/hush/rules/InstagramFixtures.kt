package app.hushfocus.hush.rules

import app.hushfocus.hush.inspector.InspectorJson
import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.NodeFlag
import app.hushfocus.hush.inspector.model.NodeInfo
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import app.hushfocus.hush.inspector.model.WindowInfo

internal object InstagramFixtures {
    val names = listOf(
        "story_viewer.json", "reel_fullscreen.json", "reels_tab_selected.json", "cached_inbox_during_reel.json",
        "dm_chat.json", "reel_from_dm_reply_box.json", "reel_reply_box_hidden.json", "explore_grid.json",
        "home_with_hidden_profile_nodes.json", "comments_over_reel.json", "share_over_reel.json",
        "more_over_reel.json", "logo_menu_two_entries.json", "following_feed.json", "favorites_feed.json",
        "feed_variant_without_title.json", "home_overlay_stories_row.json",
        "profile_own.json", "profile_other.json", "dm_inbox.json", "explore_loading_no_search_box.json",
        "search_focused_recent_results.json", "explore_with_home_tab_selected.json",
    )

    fun load(name: String): ScreenSnapshot {
        val stream = checkNotNull(javaClass.getResourceAsStream("/screens/instagram/$name"))
        return stream.bufferedReader(Charsets.UTF_8).use { InspectorJson.decode(it.readText()) as ScreenSnapshot }
    }
}

/** Clearly synthetic node lists for boundary cases and missing, disabled production signals. */
internal object SyntheticInstagram {
    val window = Bounds(0, 0, 1080, 2400)
    fun node(id: String?, bounds: Bounds, depth: Int = 1, children: Int = 0,
        clickable: Boolean = false, selected: Boolean = false, text: String? = null,
        className: String = "android.view.View", extraFlags: Set<NodeFlag> = emptySet()): NodeInfo = NodeInfo(
        depth, id, className, bounds, children,
        setOf(NodeFlag.ENABLED, NodeFlag.VISIBLE) + extraFlags +
            (if (clickable) setOf(NodeFlag.CLICKABLE) else emptySet()) +
            (if (selected) setOf(NodeFlag.SELECTED) else emptySet()), text = text,
    )

    fun snapshot(nodes: List<NodeInfo>, bounds: Bounds = window): ScreenSnapshot = ScreenSnapshot(
        1_000, InstagramRules.PACKAGE_NAME, "synthetic", listOf(
            WindowInfo(InstagramRules.APPLICATION_WINDOW, 1, true, true, bounds, null)),
        listOf(node(null, bounds, depth = 0, children = nodes.count { it.depth == 1 })) + nodes, false, false,
    )

    fun tabs(
        reels: Bounds = Bounds(216, 2200, 432, 2340),
        peers: List<Pair<String, Bounds>> = listOf(
            InstagramRules.HOME_TAB to Bounds(0, 2200, 216, 2340),
            InstagramRules.DMS_TAB to Bounds(432, 2200, 648, 2340),
            InstagramRules.SEARCH_TAB to Bounds(648, 2200, 864, 2340),
            InstagramRules.PROFILE_TAB to Bounds(864, 2200, 1080, 2340)),
        bar: Bounds? = Bounds(0, 2200, 1080, 2340),
    ): ScreenSnapshot {
        val depth = if (bar == null) 1 else 2
        return snapshot(buildList {
            if (bar != null) add(node(InstagramRules.TAB_BAR, bar, children = peers.size + 1))
            add(node(InstagramRules.REELS_TAB, reels, depth, clickable = true))
            for ((id, bounds) in peers) add(node(id, bounds, depth, clickable = true,
                selected = id == InstagramRules.HOME_TAB))
        })
    }

    fun menu(entries: Int): ScreenSnapshot = snapshot(buildList {
        add(node(InstagramRules.CONTEXT_MENU, Bounds(200, 200, 800, 1000), children = 1))
        add(node(InstagramRules.CONTEXT_MENU_LIST, Bounds(200, 200, 800, 1000), depth = 2, children = entries))
        repeat(entries) { index ->
            val top = 200 + index * 200
            add(node(InstagramRules.CONTEXT_MENU_ROW, Bounds(200, top, 800, top + 200), 3,
                children = 1, clickable = true))
            add(node(InstagramRules.CONTEXT_MENU_LABEL, Bounds(250, top + 20, 700, top + 150), 4,
                text = "synthetic-token-$index"))
        }
    })
}
