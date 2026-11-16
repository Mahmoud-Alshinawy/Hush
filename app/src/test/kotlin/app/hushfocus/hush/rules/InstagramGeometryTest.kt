package app.hushfocus.hush.rules

import app.hushfocus.hush.core.CoverKind
import app.hushfocus.hush.core.CoverRect
import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class InstagramGeometryTest {
    private val detector = InstagramScreenDetector()
    private fun cover(snapshot: ScreenSnapshot) = detector.detect(snapshot).coverBounds[CoverKind.REELS_TAB]
    private fun peers(top: Int, bottom: Int) = listOf(
        InstagramRules.HOME_TAB to Bounds(0, top, 216, bottom),
        InstagramRules.DMS_TAB to Bounds(432, top, 648, bottom),
        InstagramRules.SEARCH_TAB to Bounds(648, top, 864, bottom),
        InstagramRules.PROFILE_TAB to Bounds(864, top, 1080, bottom),
    )

    @Test fun everyReelsCoverCheckPassingReturnsExactTabBounds() {
        assertEquals(CoverRect(216, 2200, 432, 2340), cover(SyntheticInstagram.tabs()))
        assertEquals(CoverRect(216, 2154, 432, 2298),
            cover(InstagramFixtures.load("home_with_hidden_profile_nodes.json")))
    }

    @Test fun tabOutsideWindowGetsNoCover() {
        val others = listOf(InstagramRules.HOME_TAB to Bounds(0, 2200, 100, 2340),
            InstagramRules.DMS_TAB to Bounds(100, 2200, 200, 2340),
            InstagramRules.SEARCH_TAB to Bounds(200, 2200, 300, 2340))
        assertNull(cover(SyntheticInstagram.tabs(Bounds(884, 2200, 1100, 2340), others, bar = null)))
    }

    @Test fun tabWiderThanOneThirdGetsNoCover() {
        val others = listOf(InstagramRules.HOME_TAB to Bounds(0, 2200, 216, 2340),
            InstagramRules.DMS_TAB to Bounds(616, 2200, 716, 2340),
            InstagramRules.SEARCH_TAB to Bounds(716, 2200, 816, 2340))
        assertNull(cover(SyntheticInstagram.tabs(Bounds(216, 2200, 616, 2340), others)))
    }

    @Test fun tabTallerThanOneQuarterGetsNoCover() {
        assertNull(cover(SyntheticInstagram.tabs(Bounds(216, 1600, 432, 2301), peers(1600, 2301),
            Bounds(0, 1600, 1080, 2301))))
    }

    @Test fun centreAboveSeventyPercentGetsNoCover() {
        assertNull(cover(SyntheticInstagram.tabs(Bounds(216, 1400, 432, 1540), peers(1400, 1540),
            Bounds(0, 1400, 1080, 1540))))
    }

    @Test fun fewerThanTwoOtherTabsGetsNoCover() {
        assertNull(cover(SyntheticInstagram.tabs(peers = peers(2200, 2340).take(1))))
    }

    @Test fun peersOnDifferentRowDoNotQualify() {
        assertNull(cover(SyntheticInstagram.tabs(peers = peers(2100, 2240), bar = Bounds(0, 2100, 1080, 2340))))
    }

    @Test fun overlappingPeerGetsNoCoverEvenWithOtherValidPeers() {
        val others = peers(2200, 2340).map { (id, bounds) ->
            id to if (id == InstagramRules.DMS_TAB) bounds.copy(left = 400) else bounds
        }
        assertNull(cover(SyntheticInstagram.tabs(peers = others)))
    }

    @Test fun tabOutsideVisibleTabBarGetsNoCover() {
        assertNull(cover(SyntheticInstagram.tabs(bar = Bounds(0, 2200, 300, 2340))))
    }

    @Test fun missingTabBarIsAllowedWhenAllOtherChecksPass() {
        assertEquals(CoverRect(216, 2200, 432, 2340), cover(SyntheticInstagram.tabs(bar = null)))
    }

    @Test fun exactWidthHeightAndCentreLimitsAreInclusive() {
        val others = listOf(InstagramRules.HOME_TAB to Bounds(0, 1800, 180, 2400),
            InstagramRules.DMS_TAB to Bounds(540, 1800, 720, 2400),
            InstagramRules.SEARCH_TAB to Bounds(720, 1800, 900, 2400))
        assertEquals(CoverRect(180, 1800, 540, 2400), cover(SyntheticInstagram.tabs(
            Bounds(180, 1800, 540, 2400), others, Bounds(0, 1800, 1080, 2400))))
        assertEquals(CoverRect(216, 1540, 432, 1820), cover(SyntheticInstagram.tabs(
            Bounds(216, 1540, 432, 1820), peers(1540, 1820), Bounds(0, 1540, 1080, 1820))))
    }

    @Test fun windowOffsetsAreRespected() {
        val snapshot = SyntheticInstagram.tabs().translated(100, 10000)
        assertEquals(CoverRect(316, 12200, 532, 12340), cover(snapshot))
        val tooHigh = SyntheticInstagram.tabs(Bounds(216, 1400, 432, 1540), peers(1400, 1540),
            Bounds(0, 1400, 1080, 1540)).translated(100, 10000)
        assertNull(cover(tooHigh))
    }

    @Test fun homePanelExcludesToolbarStoriesAndBottomTabs() {
        for (name in listOf("home_with_hidden_profile_nodes.json", "home_overlay_stories_row.json")) {
            val bounds = detector.detect(InstagramFixtures.load(name)).coverBounds
            assertEquals(CoverRect(0, 635, 1080, 2154), bounds[CoverKind.FEED], name)
            assertEquals(CoverRect(0, 282, 1080, 635), bounds[CoverKind.STORIES_ROW], name)
        }
    }

    @Test fun exploreCoverExcludesSearchToolbarAndTabs() {
        val bounds = detector.detect(InstagramFixtures.load("explore_grid.json")).coverBounds
        assertEquals(CoverRect(0, 282, 1080, 2154), bounds[CoverKind.EXPLORE])
    }

    @Test fun storyViewerGetsVerifiedFullWindowBounds() {
        assertEquals(CoverRect(0, 0, 1080, 2412), detector.detect(
            InstagramFixtures.load("story_viewer.json")).coverBounds[CoverKind.STORY_VIEWER])
    }

    @Test fun playerAndSheetsNeverOfferReelsTabCover() {
        for (name in listOf("reels_tab_selected.json", "reel_from_dm_reply_box.json", "comments_over_reel.json",
            "share_over_reel.json", "more_over_reel.json")) assertNull(cover(InstagramFixtures.load(name)), name)
    }

    @Test fun visibleBackComposerAndReplyBoxSuppressTabCover() {
        val tabs = SyntheticInstagram.tabs()
        for (id in listOf(InstagramRules.VARIANT_BACK, InstagramRules.DM_COMPOSER)) {
            assertNull(cover(tabs.copy(nodes = tabs.nodes + SyntheticInstagram.node(id, Bounds(0, 0, 100, 100)))))
        }
        val reply = tabs.nodes + listOf(
            SyntheticInstagram.node(InstagramRules.CLIPS_ROOT, Bounds(0, 0, 1080, 2340), children = 1),
            SyntheticInstagram.node(InstagramRules.REPLY_BOX, Bounds(0, 2000, 1080, 2150), depth = 2))
        val observation = detector.detect(tabs.copy(nodes = reply))
        assertEquals(true, observation.replyBoxVisible)
        assertFalse(CoverKind.REELS_TAB in observation.coverBounds)
    }

    private fun ScreenSnapshot.translated(dx: Int, dy: Int): ScreenSnapshot {
        fun Bounds.moved() = Bounds(left + dx, top + dy, right + dx, bottom + dy)
        return copy(windows = windows.map { it.copy(bounds = it.bounds.moved()) },
            nodes = nodes.map { it.copy(bounds = it.bounds.moved()) })
    }
}
