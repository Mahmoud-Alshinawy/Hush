package app.hushfocus.hush.rules

import app.hushfocus.hush.core.ActionTarget
import app.hushfocus.hush.core.CoverKind
import app.hushfocus.hush.core.CoverRect
import app.hushfocus.hush.core.InstagramScreen
import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.NodeFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic-only patterns. These are not claims of positive on-device verification. */
class InstagramSyntheticSignalsTest {
    private val signals = InstagramRules.SupplementalSignals(profileRoot = "synthetic:profile-root",
        inboxRoot = "synthetic:inbox-root", searchResultsRoot = "synthetic:results-root",
        caughtUpRow = "synthetic:caught-up-row", olderPostsLink = "synthetic:older-posts-link")
    private val detector = InstagramScreenDetector(signals)

    @Test fun absentCaptureSignalsAndSyntheticIdsAreDisabledInProductionRules() {
        assertEquals(null, InstagramRules.supplementalSignals.caughtUpRow)
        assertEquals(null, InstagramRules.supplementalSignals.olderPostsLink)
        for (id in listOf(signals.profileRoot, signals.inboxRoot, signals.searchResultsRoot)) {
            val snapshot = SyntheticInstagram.snapshot(listOf(SyntheticInstagram.node(id, Bounds(0, 100, 1080, 2200))))
            assertEquals(InstagramScreen.UNKNOWN, InstagramScreenDetector().detect(snapshot).screen)
        }
    }

    @Test fun provisionalProfileInboxAndResultsUseExplicitSyntheticIdsOnly() {
        for ((id, expected) in listOf(signals.profileRoot to InstagramScreen.PROFILE,
            signals.inboxRoot to InstagramScreen.DM_INBOX, signals.searchResultsRoot to InstagramScreen.SEARCH_RESULTS)) {
            val snapshot = SyntheticInstagram.snapshot(listOf(SyntheticInstagram.node(id, Bounds(0, 100, 1080, 2200))))
            assertEquals(expected, detector.detect(snapshot).screen)
        }
    }

    @Test fun searchFocusIsAFlagAndDoesNotInspectQueryText() {
        val snapshot = SyntheticInstagram.snapshot(listOf(
            SyntheticInstagram.node(signals.searchResultsRoot, Bounds(0, 100, 1080, 2200)),
            SyntheticInstagram.node(InstagramRules.SEARCH_BOX, Bounds(0, 0, 1080, 100),
                clickable = true, text = "opaque-query", extraFlags = setOf(NodeFlag.EDITABLE, NodeFlag.FOCUSED))))
        val observation = detector.detect(snapshot)
        assertEquals(InstagramScreen.SEARCH_RESULTS, observation.screen)
        assertTrue(observation.searchBoxFocused)
        assertTrue(ActionTarget.SEARCH_BOX in observation.targets)
    }

    @Test fun caughtUpThirtyPercentBoundaryAndFullContentPanel() {
        val below = detector.detect(caughtUp(1871))
        assertFalse(below.caughtUpRowReached)
        assertFalse(CoverKind.CAUGHT_UP in below.coverBounds)
        val boundary = detector.detect(caughtUp(1870))
        assertTrue(boundary.caughtUpRowReached)
        assertEquals(CoverRect(0, 100, 1080, 2200), boundary.coverBounds[CoverKind.CAUGHT_UP])
        assertTrue(ActionTarget.OLDER_POSTS_LINK in boundary.targets)
        assertTrue(ActionTarget.FEED_ABOVE_CAUGHT_UP in boundary.targets)
    }

    @Test fun caughtUpSyntheticPatternDoesNotEnableProductionDetection() {
        assertFalse(InstagramScreenDetector().detect(caughtUp(1800)).caughtUpRowReached)
    }

    private fun caughtUp(rowTop: Int) = SyntheticInstagram.snapshot(listOf(
        SyntheticInstagram.node(InstagramRules.HOME_CONTENT, Bounds(0, 100, 1080, 2200), children = 3),
        SyntheticInstagram.node(InstagramRules.HOME_VIEWPORT, Bounds(0, 200, 1080, 1900), depth = 2,
            children = 1, extraFlags = setOf(NodeFlag.SCROLLABLE)),
        SyntheticInstagram.node(signals.caughtUpRow, Bounds(0, rowTop, 1080, rowTop + 100), depth = 3, children = 1),
        SyntheticInstagram.node(signals.olderPostsLink, Bounds(100, rowTop + 1, 500, minOf(rowTop + 99, 1899)),
            depth = 4, clickable = true),
        SyntheticInstagram.node(InstagramRules.HOME_BAR, Bounds(0, 100, 1080, 200), depth = 2),
        SyntheticInstagram.node(InstagramRules.TAB_BAR, Bounds(0, 1900, 1080, 2200), depth = 2, children = 3),
        SyntheticInstagram.node(InstagramRules.HOME_TAB, Bounds(0, 1900, 216, 2200), depth = 3, clickable = true, selected = true),
        SyntheticInstagram.node(InstagramRules.REELS_TAB, Bounds(216, 1900, 432, 2200), depth = 3, clickable = true),
        SyntheticInstagram.node(InstagramRules.DMS_TAB, Bounds(432, 1900, 648, 2200), depth = 3, clickable = true),
    ))
}
