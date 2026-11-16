package app.hushfocus.hush.rules

import app.hushfocus.hush.core.ActionTarget
import app.hushfocus.hush.core.AuthorKey
import app.hushfocus.hush.core.CoverKind
import app.hushfocus.hush.core.CoverRect
import app.hushfocus.hush.core.InstagramScreen
import app.hushfocus.hush.inspector.LabelAllowList
import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.NodeFlag
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstagramScreenDetectorTest {
    private val detector = InstagramScreenDetector()
    private val tabs = setOf(ActionTarget.HOME_TAB, ActionTarget.DMS_TAB, ActionTarget.SEARCH_TAB)
    private val tabCover = mapOf(CoverKind.REELS_TAB to CoverRect(216, 2154, 432, 2298))
    private val homeCovers = tabCover + mapOf(CoverKind.FEED to CoverRect(0, 635, 1080, 2154),
        CoverKind.STORIES_ROW to CoverRect(0, 282, 1080, 635))
    private val exploreCovers = tabCover + mapOf(CoverKind.EXPLORE to CoverRect(0, 282, 1080, 2154))
    private data class Expected(
        val screen: InstagramScreen, val player: Boolean = false, val story: Boolean = false,
        val selected: Boolean = false, val reply: Boolean = false, val sheet: Boolean = false,
        val bar: Boolean = false, val back: Boolean = false, val composer: Boolean = false,
        val page: Int? = null, val authors: Int = 0, val targets: Set<ActionTarget> = emptySet(),
        val menu: Int = 0, val title: String? = null, val searchFocused: Boolean = false,
        val covers: Map<CoverKind, CoverRect> = emptyMap(),
    )
    private val expected = linkedMapOf(
        "story_viewer.json" to Expected(InstagramScreen.STORY_VIEWER, story = true,
            covers = mapOf(CoverKind.STORY_VIEWER to CoverRect(0, 0, 1080, 2412))),
        "reel_fullscreen.json" to Expected(InstagramScreen.REEL_PLAYER, player = true, page = 0, authors = 1),
        "reels_tab_selected.json" to Expected(InstagramScreen.REELS_TAB, player = true, selected = true,
            bar = true, page = 0, authors = 1, targets = tabs),
        "cached_inbox_during_reel.json" to Expected(InstagramScreen.REEL_PLAYER, player = true,
            bar = true, page = 1, authors = 1),
        "dm_chat.json" to Expected(InstagramScreen.DM_CHAT, back = true, composer = true),
        "reel_from_dm_reply_box.json" to Expected(InstagramScreen.REEL_PLAYER, player = true,
            reply = true, page = 0, authors = 1),
        "reel_reply_box_hidden.json" to Expected(InstagramScreen.REEL_PLAYER, player = true, page = 1, authors = 1),
        "explore_grid.json" to Expected(InstagramScreen.EXPLORE, bar = true,
            targets = tabs + ActionTarget.SEARCH_BOX, covers = exploreCovers),
        "home_with_hidden_profile_nodes.json" to Expected(InstagramScreen.HOME_FEED, bar = true,
            targets = tabs + ActionTarget.INSTAGRAM_LOGO, covers = homeCovers),
        "comments_over_reel.json" to Expected(InstagramScreen.OVERLAY_UI, player = true, sheet = true, page = 0, authors = 1),
        "share_over_reel.json" to Expected(InstagramScreen.OVERLAY_UI, player = true, sheet = true, page = 0, authors = 1),
        "more_over_reel.json" to Expected(InstagramScreen.OVERLAY_UI, player = true, sheet = true, page = 0, authors = 1),
        "logo_menu_two_entries.json" to Expected(InstagramScreen.OVERLAY_UI, sheet = true, menu = 2,
            targets = setOf(ActionTarget.FOLLOWING_MENU_ENTRY, ActionTarget.FAVORITES_MENU_ENTRY)),
        "following_feed.json" to Expected(InstagramScreen.FOLLOWING, back = true, title = "Following"),
        "favorites_feed.json" to Expected(InstagramScreen.FAVORITES, back = true, title = "Favorites"),
        "feed_variant_without_title.json" to Expected(InstagramScreen.UNKNOWN),
        "home_overlay_stories_row.json" to Expected(InstagramScreen.HOME_FEED, bar = true,
            targets = tabs + ActionTarget.INSTAGRAM_LOGO, covers = homeCovers),
        "profile_own.json" to Expected(InstagramScreen.PROFILE, bar = true, targets = tabs, covers = tabCover),
        "profile_other.json" to Expected(InstagramScreen.PROFILE, back = true),
        "dm_inbox.json" to Expected(InstagramScreen.DM_INBOX, bar = true, targets = tabs, covers = tabCover),
        "explore_loading_no_search_box.json" to Expected(InstagramScreen.UNKNOWN, bar = true),
        "search_focused_recent_results.json" to Expected(InstagramScreen.SEARCH_RESULTS, back = true,
            targets = setOf(ActionTarget.SEARCH_BOX), searchFocused = true),
        "explore_with_home_tab_selected.json" to Expected(InstagramScreen.EXPLORE,
            bar = true, targets = tabs + ActionTarget.SEARCH_BOX, covers = exploreCovers),
    )

    @Test fun everyRealFixtureHasExpectedScreenAndObservationFields() {
        assertEquals(InstagramFixtures.names.toSet(), expected.keys)
        for ((name, e) in expected) {
            val snapshot = InstagramFixtures.load(name)
            val o = detector.detect(snapshot, logoMenuExpected = e.menu > 0, expectedTitleToken = e.title)
            assertEquals(e.screen, o.screen, name)
            assertEquals(snapshot.timeMs, o.atMs, name)
            assertEquals(e.player, o.reelPlayerVisible, name)
            assertEquals(e.story, o.storyViewerVisible, name)
            assertEquals(e.selected, o.reelsTabSelected, name)
            assertEquals(e.reply, o.replyBoxVisible, name)
            assertEquals(e.sheet, o.sheetOrMenuOpen, name)
            assertEquals(e.bar, o.tabBarVisible, name)
            assertEquals(e.back, o.backArrowVisible, name)
            assertEquals(e.composer, o.dmComposerVisible, name)
            assertEquals(e.page, o.pageIndex, name)
            assertEquals(e.authors, o.authorKeys.size, name)
            if (e.authors == 1) {
                val authorToken = snapshot.nodes.single {
                    it.viewId == InstagramRules.REEL_AUTHOR && NodeFlag.VISIBLE in it.flags
                }.text!!
                assertEquals(listOf(AuthorKey(authorToken)), o.authorKeys, name)
            }
            assertEquals(e.targets, o.targets, name)
            assertEquals(e.covers, o.coverBounds, name)
            assertEquals(e.menu, o.menuEntryCount, name)
            assertEquals(e.title != null, o.destinationTitleMatches, name)
            assertEquals(e.searchFocused, o.searchBoxFocused, name)
            assertFalse(o.caughtUpRowReached, name)
            assertFalse(o.usageAccessGranted, name)
            assertFalse(o.scrollCompleted, name)
            assertFalse(o.olderPostsOpened, name)
        }
    }

    @Test fun hiddenProfileAndExploreAndInboxNodesCannotOverrideHome() {
        val snapshot = InstagramFixtures.load("home_with_hidden_profile_nodes.json")
        assertTrue(snapshot.nodes.any { it.viewId?.contains("profile_header") == true })
        val profileId = snapshot.nodes.first { it.viewId?.endsWith("profile_header_container") == true }.viewId
        val o = InstagramScreenDetector(InstagramRules.SupplementalSignals(profileRoot = profileId)).detect(snapshot)
        assertEquals(InstagramScreen.HOME_FEED, o.screen)
        assertFalse(o.reelPlayerVisible)
        assertFalse(o.dmComposerVisible)
    }

    @Test fun visibleChildrenUnderHiddenZeroWidthAncestorAreNotInboxEvidence() {
        val snapshot = InstagramFixtures.load("cached_inbox_during_reel.json")
        assertTrue(snapshot.nodes.any { it.viewId?.endsWith("direct_inbox_action_bar") == true && NodeFlag.VISIBLE in it.flags })
        val inboxId = snapshot.nodes.single { it.viewId?.endsWith("direct_inbox_action_bar") == true }.viewId
        assertEquals(InstagramScreen.REEL_PLAYER, InstagramScreenDetector(
            InstagramRules.SupplementalSignals(inboxRoot = inboxId)).detect(snapshot).screen)
    }

    @Test fun replacingAllAllowListedLabelsWithOpaqueTokensChangesNoDetectionResult() {
        val aliases = InstagramRules.inspectorLabels.sortedByDescending { it.length }
            .mapIndexed { index, label -> label to "opaque-ui-$index" }
        fun replace(value: String?): String? = value?.let { original ->
            aliases.fold(original) { text, (label, opaque) -> text.replace(label, opaque) }
        }
        for ((name, e) in expected) {
            val snapshot = InstagramFixtures.load(name)
            val replaced = snapshot.copy(
                windows = snapshot.windows.map { it.copy(title = replace(it.title)) },
                nodes = snapshot.nodes.map { it.copy(text = replace(it.text), contentDescription = replace(it.contentDescription),
                    hintText = replace(it.hintText), paneTitle = replace(it.paneTitle), stateDescription = replace(it.stateDescription)) },
            )
            assertEquals(detector.detect(snapshot, e.menu > 0, e.title),
                detector.detect(replaced, e.menu > 0, replace(e.title)), name)
        }
    }

    @Test fun followingAndFavoritesAreStructuralEvenWhenTitleTokensAreSwapped() {
        val following = InstagramFixtures.load("following_feed.json").replaceTitle("opaque-unrelated")
        val favorites = InstagramFixtures.load("favorites_feed.json").replaceTitle("opaque-unrelated")
        assertEquals(InstagramScreen.FOLLOWING, detector.detect(following).screen)
        assertEquals(InstagramScreen.FAVORITES, detector.detect(favorites).screen)
        assertTrue(detector.detect(favorites, expectedTitleToken = "opaque-unrelated").destinationTitleMatches)
        assertFalse(detector.detect(favorites, expectedTitleToken = "opaque-different").destinationTitleMatches)
    }

    @Test fun logoMenuNeedsExplicitWorkflowExpectation() {
        val menu = InstagramFixtures.load("logo_menu_two_entries.json")
        assertEquals(0, detector.detect(menu).menuEntryCount)
        assertTrue(detector.detect(menu).targets.isEmpty())
        assertEquals(2, detector.detect(menu, logoMenuExpected = true).menuEntryCount)
        for (name in listOf("comments_over_reel.json", "share_over_reel.json", "more_over_reel.json")) {
            assertEquals(0, detector.detect(InstagramFixtures.load(name), logoMenuExpected = true).menuEntryCount)
        }
    }

    @Test fun threeEntryLogoMenuNeverProvidesEntryTargets() {
        val o = detector.detect(SyntheticInstagram.menu(3), logoMenuExpected = true)
        assertEquals(3, o.menuEntryCount)
        assertFalse(ActionTarget.FOLLOWING_MENU_ENTRY in o.targets)
        assertFalse(ActionTarget.FAVORITES_MENU_ENTRY in o.targets)
    }

    @Test fun missingMenuLabelTokenPreventsUnconfirmableEntryTaps() {
        val menu = SyntheticInstagram.menu(2)
        val o = detector.detect(menu.copy(nodes = menu.nodes.map {
            if (it.viewId == InstagramRules.CONTEXT_MENU_LABEL) it.copy(text = null) else it
        }), logoMenuExpected = true)
        assertEquals(2, o.menuEntryCount)
        assertTrue(o.targets.isEmpty())
    }

    @Test fun invalidInputAndForeignPackagesFailClosedForTargetsAndGeometry() {
        val home = InstagramFixtures.load("home_with_hidden_profile_nodes.json")
        val cases = listOf(home.copy(packageName = "synthetic.other"), home.copy(windows = emptyList()),
            home.copy(nodeCapHit = true), home.copy(depthCapHit = true), home.copy(nodes = emptyList()),
            home.copy(nodes = home.nodes.mapIndexed { index, n -> if (index == 1) n.copy(depth = 50) else n }))
        for (snapshot in cases) {
            val o = detector.detect(snapshot)
            assertEquals(InstagramScreen.UNKNOWN, o.screen)
            assertTrue(o.targets.isEmpty())
            assertTrue(o.coverBounds.isEmpty())
        }
    }

    @Test fun inspectorInstagramLabelsNowComeFromRules() {
        assertEquals(InstagramRules.inspectorLabels, LabelAllowList.labelsFor(InstagramRules.PACKAGE_NAME))
    }

    @Test fun duplicateAuthorLabelsRemainTwoLabelsEvenWhenTokensMatch() {
        val snapshot = InstagramFixtures.load("reel_from_dm_reply_box.json")
        val index = snapshot.nodes.indexOfFirst { it.viewId == InstagramRules.REEL_AUTHOR }
        val author = snapshot.nodes[index]
        val nodes = snapshot.nodes.toMutableList().apply { add(index + 1, author) }
        assertEquals(listOf(AuthorKey(author.text!!), AuthorKey(author.text)),
            detector.detect(snapshot.copy(nodes = nodes)).authorKeys)
    }

    @Test fun absentAuthorTokenDoesNotSupplyAnIdentity() {
        val snapshot = InstagramFixtures.load("reel_from_dm_reply_box.json")
        val observation = detector.detect(snapshot.copy(nodes = snapshot.nodes.map {
            if (it.viewId == InstagramRules.REEL_AUTHOR) it.copy(text = null) else it
        }))
        assertTrue(observation.authorKeys.isEmpty())
    }

    @Test fun positiveVisibleInWindowIsRequiredForEachSignal() {
        val signal = SyntheticInstagram.node(InstagramRules.PROFILE_ROOT, Bounds(0, 100, 1080, 2200))
        assertEquals(InstagramScreen.PROFILE, detector.detect(SyntheticInstagram.snapshot(listOf(signal))).screen)
        for (invalid in listOf(signal.copy(flags = signal.flags - NodeFlag.VISIBLE),
            signal.copy(bounds = Bounds(0, 100, 0, 2200)), signal.copy(bounds = Bounds(0, 100, 1081, 2200)))) {
            val observation = detector.detect(SyntheticInstagram.snapshot(listOf(invalid)))
            assertEquals(InstagramScreen.UNKNOWN, observation.screen)
            assertTrue(observation.targets.isEmpty())
            assertTrue(observation.coverBounds.isEmpty())
        }
    }

    private fun ScreenSnapshot.replaceTitle(token: String) = copy(nodes = nodes.map {
        if (it.viewId == InstagramRules.VARIANT_TITLE) it.copy(text = token, contentDescription = token) else it
    })
}
