package app.hushfocus.hush.rules

import app.hushfocus.hush.core.ActionTarget
import app.hushfocus.hush.core.AuthorKey
import app.hushfocus.hush.core.CoverKind
import app.hushfocus.hush.core.CoverRect
import app.hushfocus.hush.core.InstagramObservation
import app.hushfocus.hush.core.InstagramScreen
import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.NodeFlag
import app.hushfocus.hush.inspector.model.NodeInfo
import app.hushfocus.hush.inspector.model.ScreenSnapshot

class InstagramScreenDetector(
    private val supplemental: InstagramRules.SupplementalSignals = InstagramRules.supplementalSignals,
) {
    /**
     * The workflow supplies logoMenuExpected only after a verified logo tap; title tokens are opaque.
     * Permission state and scroll/link completion remain for the app/executor layer to supply.
     */
    fun detect(
        snapshot: ScreenSnapshot,
        logoMenuExpected: Boolean = false,
        expectedTitleToken: String? = null,
    ): InstagramObservation {
        val tree = InstagramTree(snapshot)
        if (!tree.reliable) return InstagramObservation(snapshot.timeMs, InstagramScreen.UNKNOWN)
        val pager = verifiedPager(tree)
        val story = tree.unique(InstagramRules.STORY_ROOT)
        val thread = tree.unique(InstagramRules.DM_THREAD)
        val messages = tree.unique(InstagramRules.DM_MESSAGE_LIST)
        val chat = thread != null && messages != null && tree.inside(messages, thread)
        val profile = tree.unique(supplemental.profileRoot) != null
        val inbox = tree.unique(supplemental.inboxRoot) != null
        val results = tree.unique(supplemental.searchResultsRoot) != null
        val sheet = InstagramRules.SHEET_ROOTS.any { tree.matching(it).isNotEmpty() }
        val reelsSelected = tree.unique(InstagramRules.REELS_TAB)?.let {
            NodeFlag.SELECTED in tree.nodes[it].flags
        } ?: false
        val explore = isExplore(tree)
        val variant = feedVariant(tree)
        val home = tree.unique(InstagramRules.HOME_TAB)?.let {
            NodeFlag.SELECTED in tree.nodes[it].flags
        } == true || (tree.unique(InstagramRules.HOME_BAR) != null &&
            tree.unique(InstagramRules.HOME_VIEWPORT) != null)
        val conflictingRoots = listOf(chat, profile, inbox, story != null, pager != null).count { it } > 1
        val screen = when {
            sheet -> InstagramScreen.OVERLAY_UI
            conflictingRoots -> InstagramScreen.UNKNOWN
            chat -> InstagramScreen.DM_CHAT
            story != null -> InstagramScreen.STORY_VIEWER
            profile -> InstagramScreen.PROFILE
            inbox -> InstagramScreen.DM_INBOX
            reelsSelected -> InstagramScreen.REELS_TAB
            pager != null -> InstagramScreen.REEL_PLAYER
            variant != null -> variant
            results -> InstagramScreen.SEARCH_RESULTS
            explore -> InstagramScreen.EXPLORE
            home -> InstagramScreen.HOME_FEED
            else -> InstagramScreen.UNKNOWN
        }

        val reply = tree.unique(InstagramRules.REPLY_BOX)
        val clipsRoot = tree.unique(InstagramRules.CLIPS_ROOT)
        val replyVisible = reply != null && clipsRoot != null && tree.inside(reply, clipsRoot)
        val back = tree.unique(InstagramRules.VARIANT_BACK) != null || tree.unique(InstagramRules.DM_BACK) != null ||
            profileBackVisible(tree)
        val composer = tree.unique(InstagramRules.DM_COMPOSER) != null
        val bar = tree.unique(InstagramRules.TAB_BAR)
        val menuRows = if (logoMenuExpected) verifiedMenuRows(tree) else emptyList()
        val targets = mutableSetOf<ActionTarget>()
        if (screen != InstagramScreen.UNKNOWN) {
            for ((id, target) in listOf(InstagramRules.DMS_TAB to ActionTarget.DMS_TAB,
                InstagramRules.HOME_TAB to ActionTarget.HOME_TAB, InstagramRules.SEARCH_TAB to ActionTarget.SEARCH_TAB)) {
                if (tree.unique(id)?.let(tree::clickable) == true) targets += target
            }
            val search = tree.unique(InstagramRules.SEARCH_BOX)
            if ((screen == InstagramScreen.EXPLORE || screen == InstagramScreen.SEARCH_RESULTS) &&
                search?.let(tree::clickable) == true
            ) targets += ActionTarget.SEARCH_BOX
            val logo = tree.unique(InstagramRules.HOME_LOGO)
            val titleControl = tree.unique(InstagramRules.HOME_TITLE_CONTROL)
            val homeBar = tree.unique(InstagramRules.HOME_BAR)
            if (screen == InstagramScreen.HOME_FEED && logo != null && titleControl != null && homeBar != null &&
                tree.inside(logo, titleControl) && tree.inside(titleControl, homeBar) && tree.clickable(titleControl)
            ) targets += ActionTarget.INSTAGRAM_LOGO
            if (menuRows.size == InstagramRules.LOGO_MENU_ENTRIES && menuRows.all { row ->
                    tree.clickable(row) && tree.matching(InstagramRules.CONTEXT_MENU_LABEL)
                        .singleOrNull { tree.inside(it, row) }?.let { !tree.nodes[it].text.isNullOrEmpty() } == true
                }
            ) {
                targets += ActionTarget.FOLLOWING_MENU_ENTRY
                targets += ActionTarget.FAVORITES_MENU_ENTRY
            }
        }

        val covers = coverBounds(tree, screen)
        val caughtUp = caughtUpReached(tree, covers[CoverKind.FEED])
        if (caughtUp) {
            tree.unique(InstagramRules.HOME_CONTENT)?.let { content ->
                val bounds = tree.nodes[content].bounds
                val enclosed = listOfNotNull(tree.unique(InstagramRules.HOME_VIEWPORT),
                    tree.unique(InstagramRules.HOME_BAR), tree.unique(InstagramRules.TAB_BAR))
                if (enclosed.all { boundsContain(bounds, tree.nodes[it].bounds) }) {
                    covers[CoverKind.CAUGHT_UP] = bounds.toCoverRect()
                }
            }
            val row = tree.unique(supplemental.caughtUpRow)
            val link = tree.unique(supplemental.olderPostsLink)
            if (row != null && link != null && tree.inside(link, row) && tree.clickable(link)) {
                targets += ActionTarget.OLDER_POSTS_LINK
            }
            tree.unique(InstagramRules.HOME_VIEWPORT)?.let {
                if (NodeFlag.SCROLLABLE in tree.nodes[it].flags) targets += ActionTarget.FEED_ABOVE_CAUGHT_UP
            }
        }
        if (screen != InstagramScreen.UNKNOWN && !sheet && !back && !composer && story == null &&
            pager == null && !replyVisible
        ) verifiedReelsTabBounds(tree)?.let { covers[CoverKind.REELS_TAB] = it.toCoverRect() }

        val title = tree.unique(InstagramRules.VARIANT_TITLE)?.let { tree.nodes[it].text }
        val titleMatches = (screen == InstagramScreen.FOLLOWING || screen == InstagramScreen.FAVORITES) &&
            !expectedTitleToken.isNullOrEmpty() && title == expectedTitleToken
        val authorNodes = pager?.let { root -> tree.matching(InstagramRules.REEL_AUTHOR).filter { tree.inside(it, root) } }
            .orEmpty()
        val authorKeys = if (authorNodes.all { !tree.nodes[it].text.isNullOrEmpty() }) {
            authorNodes.map { AuthorKey(tree.nodes[it].text!!) }
        } else emptyList()
        val pageRows = pager?.let(tree::pageRows).orEmpty()
        val searchBox = tree.unique(InstagramRules.SEARCH_BOX)
        return InstagramObservation(
            atMs = snapshot.timeMs, screen = screen,
            reelPlayerVisible = pager != null, storyViewerVisible = story != null,
            reelsTabSelected = reelsSelected, replyBoxVisible = replyVisible,
            sheetOrMenuOpen = sheet, tabBarVisible = bar != null, backArrowVisible = back,
            dmComposerVisible = composer, pageIndex = pageRows.singleOrNull(), authorKeys = authorKeys,
            targets = targets, coverBounds = covers, caughtUpRowReached = caughtUp,
            menuEntryCount = menuRows.size,
            destinationTitleMatches = titleMatches,
            searchBoxFocused = searchBox?.let { NodeFlag.FOCUSED in tree.nodes[it].flags } ?: false,
        )
    }

    private fun isExplore(tree: InstagramTree): Boolean {
        val grid = tree.unique(InstagramRules.EXPLORE_GRID) ?: return false
        return tree.unique(InstagramRules.EXPLORE_BAR) != null && tree.unique(InstagramRules.SEARCH_BOX) != null &&
            tree.matching(InstagramRules.EXPLORE_CARD).any { tree.inside(it, grid) }
    }

    private fun profileBackVisible(tree: InstagramTree): Boolean {
        val bar = tree.unique(InstagramRules.PROFILE_BAR) ?: return false
        val left = tree.unique(InstagramRules.PROFILE_LEFT_BUTTONS) ?: return false
        if (!tree.inside(left, bar) || tree.nodes[left].childCount != 1) return false
        val button = tree.children[left].singleOrNull() ?: return false
        return tree.nodes[button].viewId == null && tree.nodes[button].className == InstagramRules.PROFILE_BACK_CLASS &&
            tree.clickable(button)
    }

    private fun feedVariant(tree: InstagramTree): InstagramScreen? {
        if (tree.unique(InstagramRules.VARIANT_LIST) == null) return null
        val bar = tree.unique(InstagramRules.VARIANT_BAR) ?: return null
        val group = tree.unique(InstagramRules.VARIANT_TITLE_GROUP) ?: return null
        val title = tree.unique(InstagramRules.VARIANT_TITLE) ?: return null
        val back = tree.unique(InstagramRules.VARIANT_BACK) ?: return null
        if (group !in tree.children[bar] || !tree.inside(title, group) || !tree.inside(back, group)) return null
        val children = tree.children[bar]
        if (children.any { !tree.eligible[it] } || children.size != tree.nodes[bar].childCount) return null
        if (children.size == InstagramRules.FOLLOWING_BAR_CHILDREN) return InstagramScreen.FOLLOWING
        if (children.size != InstagramRules.FAVORITES_BAR_CHILDREN) return null
        val extra = children.singleOrNull { it != group } ?: return null
        return if (tree.nodes[extra].className == InstagramRules.VARIANT_BUTTON_CLASS && tree.clickable(extra) &&
            !boundsOverlap(tree.nodes[extra].bounds, tree.nodes[group].bounds)
        ) InstagramScreen.FAVORITES else null
    }

    private fun verifiedMenuRows(tree: InstagramTree): List<Int> {
        val menu = tree.unique(InstagramRules.CONTEXT_MENU) ?: return emptyList()
        val list = tree.unique(InstagramRules.CONTEXT_MENU_LIST) ?: return emptyList()
        if (!tree.inside(list, menu)) return emptyList()
        val rows = tree.children[list]
        if (rows.size != tree.nodes[list].childCount || rows.any {
                tree.nodes[it].viewId != InstagramRules.CONTEXT_MENU_ROW || !tree.eligible[it]
            }
        ) return emptyList()
        val labels = tree.matching(InstagramRules.CONTEXT_MENU_LABEL)
        if (rows.any { row -> labels.count { tree.inside(it, row) } != 1 }) return emptyList()
        val ordered = rows.sortedBy { tree.nodes[it].bounds.top }
        if (ordered.zipWithNext().any { (a, b) -> boundsOverlap(tree.nodes[a].bounds, tree.nodes[b].bounds) }) {
            return emptyList()
        }
        return ordered
    }

    private fun coverBounds(tree: InstagramTree, screen: InstagramScreen): MutableMap<CoverKind, CoverRect> {
        val result = mutableMapOf<CoverKind, CoverRect>()
        val window = tree.window ?: return result
        if (screen == InstagramScreen.STORY_VIEWER) result[CoverKind.STORY_VIEWER] = window.toCoverRect()
        if (screen == InstagramScreen.HOME_FEED) {
            val viewport = tree.unique(InstagramRules.HOME_VIEWPORT)?.let { tree.nodes[it].bounds }
            val toolbar = tree.unique(InstagramRules.HOME_BAR)?.let { tree.nodes[it].bounds }
            val tabs = tree.unique(InstagramRules.TAB_BAR)?.let { tree.nodes[it].bounds }
            val stories = InstagramRules.STORIES_TRAYS.flatMap(tree::matching)
            val story = stories.singleOrNull()?.let { tree.nodes[it].bounds }
            val unsafeStories = tree.nodes.indices.any {
                tree.nodes[it].viewId in InstagramRules.STORIES_TRAYS && tree.branchActive[it] && !tree.eligible[it]
            }
            if (viewport != null && !unsafeStories && stories.size <= 1) {
                val top = maxOf(viewport.top, toolbar?.bottom ?: viewport.top, story?.bottom ?: viewport.top)
                val bottom = minOf(viewport.bottom, tabs?.top ?: viewport.bottom)
                if (top < bottom) result[CoverKind.FEED] = CoverRect(viewport.left, top, viewport.right, bottom)
                if (story != null && boundsContain(viewport, story) &&
                    (toolbar == null || !boundsOverlap(story, toolbar)) && (tabs == null || !boundsOverlap(story, tabs))
                ) result[CoverKind.STORIES_ROW] = story.toCoverRect()
            }
        }
        if (screen == InstagramScreen.EXPLORE) {
            val grid = tree.unique(InstagramRules.EXPLORE_GRID)?.let { tree.nodes[it].bounds }
            val bar = tree.unique(InstagramRules.EXPLORE_BAR)?.let { tree.nodes[it].bounds }
            val tabs = tree.unique(InstagramRules.TAB_BAR)?.let { tree.nodes[it].bounds }
            if (grid != null && bar != null) {
                val top = maxOf(grid.top, bar.bottom)
                val bottom = minOf(grid.bottom, tabs?.top ?: grid.bottom)
                if (top < bottom) result[CoverKind.EXPLORE] = CoverRect(grid.left, top, grid.right, bottom)
            }
        }
        return result
    }

    private fun caughtUpReached(tree: InstagramTree, feed: CoverRect?): Boolean {
        if (feed == null) return false
        val viewport = tree.unique(InstagramRules.HOME_VIEWPORT) ?: return false
        val row = tree.unique(supplemental.caughtUpRow) ?: return false
        if (!tree.inside(row, viewport)) return false
        val bounds = tree.nodes[row].bounds
        val visible = boundsIntersection(bounds, Bounds(feed.left, feed.top, feed.right, feed.bottom)) ?: return false
        return boundsArea(visible).toDouble() / boundsArea(bounds) >= InstagramRules.CAUGHT_UP_VISIBLE_FRACTION
    }
}

internal fun verifiedPager(tree: InstagramTree): Int? {
    val pager = tree.unique(InstagramRules.REEL_PAGER) ?: return null
    val container = tree.unique(InstagramRules.CLIPS_CONTAINER) ?: return null
    val root = tree.unique(InstagramRules.CLIPS_ROOT) ?: return null
    return pager.takeIf { tree.nodes[it].className == InstagramRules.REEL_PAGER_CLASS &&
        tree.inside(it, container) && tree.inside(container, root) &&
        boundsContain(tree.nodes[container].bounds, tree.nodes[it].bounds) }
}

internal fun verifiedReelsTabBounds(tree: InstagramTree): Bounds? {
    val window = tree.window ?: return null
    val tabIndex = tree.unique(InstagramRules.REELS_TAB) ?: return null
    if (!tree.clickable(tabIndex)) return null
    val tab = tree.nodes[tabIndex].bounds
    if (boundsWidth(tab) > boundsWidth(window) * InstagramRules.MAX_REELS_TAB_WIDTH_FRACTION ||
        boundsHeight(tab) > boundsHeight(window) * InstagramRules.MAX_REELS_TAB_HEIGHT_FRACTION ||
        (tab.top.toDouble() + tab.bottom) / 2 < window.top + boundsHeight(window) * InstagramRules.MIN_REELS_TAB_CENTRE_FRACTION
    ) return null
    val others = InstagramRules.BOTTOM_TABS.filter { it != InstagramRules.REELS_TAB }.mapNotNull(tree::unique)
    if (others.any { boundsOverlap(tab, tree.nodes[it].bounds) }) return null
    val peers = others.filter {
        tree.clickable(it) && tree.nodes[it].bounds.top == tab.top && tree.nodes[it].bounds.bottom == tab.bottom
    }
    if (peers.size < InstagramRules.MIN_OTHER_BOTTOM_TABS) return null
    val bars = tree.matching(InstagramRules.TAB_BAR)
    if (bars.size > 1) return null
    val bar = bars.singleOrNull()
    if (bar != null && !boundsContain(tree.nodes[bar].bounds, tab)) return null
    return tab
}

/** Depth-based reconstruction plus one shared visibility gate for all matching and geometry. */
internal class InstagramTree(snapshot: ScreenSnapshot) {
    val nodes = snapshot.nodes
    val parents = IntArray(nodes.size) { -1 }
    val children = List(nodes.size) { mutableListOf<Int>() }
    val eligible = BooleanArray(nodes.size)
    val branchActive = BooleanArray(nodes.size)
    val window: Bounds? = snapshot.windows.filter {
        it.type == InstagramRules.APPLICATION_WINDOW && it.active && it.focused && boundsPositive(it.bounds)
    }.singleOrNull()?.bounds
    val reliable: Boolean

    init {
        var shapeValid = nodes.isNotEmpty() && nodes.first().depth == 0
        val stack = mutableListOf<Int>()
        for ((index, node) in nodes.withIndex()) {
            if (node.depth < 0 || node.depth > stack.size || (index > 0 && node.depth == 0)) {
                shapeValid = false
                continue
            }
            while (stack.size > node.depth) stack.removeAt(stack.lastIndex)
            val parent = stack.lastOrNull()
            if (parent != null) { parents[index] = parent; children[parent] += index }
            val w = window
            branchActive[index] = w != null && NodeFlag.VISIBLE in node.flags && boundsPositive(node.bounds) &&
                boundsOverlap(w, node.bounds) && (parent == null || branchActive[parent])
            eligible[index] = branchActive[index] && w != null && boundsContain(w, node.bounds)
            stack += index
        }
        reliable = snapshot.packageName == InstagramRules.PACKAGE_NAME && window != null &&
            !snapshot.nodeCapHit && !snapshot.depthCapHit && shapeValid
        if (!reliable) { eligible.fill(false); branchActive.fill(false) }
    }

    fun matching(id: String): List<Int> = nodes.indices.filter { eligible[it] && nodes[it].viewId == id }
    fun unique(id: String?): Int? = id?.let { matching(it).singleOrNull() }
    fun clickable(index: Int): Boolean {
        if (!eligible[index] || NodeFlag.CLICKABLE !in nodes[index].flags || NodeFlag.ENABLED !in nodes[index].flags) {
            return false
        }
        var parent = parents[index]
        while (parent >= 0) {
            if (!boundsContain(nodes[parent].bounds, nodes[index].bounds)) return false
            parent = parents[parent]
        }
        return true
    }
    fun pageRows(pager: Int): List<Int> = nodes.indices.filter { index ->
        if (!eligible[index] || !inside(index, pager) ||
            nodes[index].collectionItemInfo?.rowIndex?.let { it >= 0 } != true
        ) return@filter false
        var parent = parents[index]
        while (parent >= 0 && parent != pager) {
            if (nodes[parent].collectionItemInfo != null) return@filter false
            parent = parents[parent]
        }
        parent == pager
    }.map { nodes[it].collectionItemInfo!!.rowIndex }
    fun inside(index: Int, root: Int): Boolean {
        var current = index
        while (current >= 0) { if (current == root) return true; current = parents[current] }
        return false
    }
}

internal fun boundsPositive(b: Bounds): Boolean = b.right > b.left && b.bottom > b.top
internal fun boundsWidth(b: Bounds): Long = b.right.toLong() - b.left
internal fun boundsHeight(b: Bounds): Long = b.bottom.toLong() - b.top
internal fun boundsArea(b: Bounds): Long = boundsWidth(b) * boundsHeight(b)
internal fun boundsContain(outer: Bounds, inner: Bounds): Boolean = boundsPositive(inner) &&
    inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom
internal fun boundsIntersection(a: Bounds, b: Bounds): Bounds? =
    Bounds(maxOf(a.left, b.left), maxOf(a.top, b.top), minOf(a.right, b.right), minOf(a.bottom, b.bottom))
        .takeIf(::boundsPositive)
internal fun boundsOverlap(a: Bounds, b: Bounds): Boolean = boundsIntersection(a, b) != null
internal fun Bounds.toCoverRect(): CoverRect = CoverRect(left, top, right, bottom)
