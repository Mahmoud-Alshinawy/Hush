package app.hushfocus.hush.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReelSessionTrackerTest {
    private val time = FakeTime()
    private val tracker = ReelSessionTracker(0.12)

    private fun player(reply: Boolean = false, index: Int? = 0) = InstagramObservation(
        time.nowMs, InstagramScreen.REEL_PLAYER, replyBoxVisible = reply, pageIndex = index,
    )
    private fun scroll(dy: Int, dx: Int = 0, source: SwipeSource = SwipeSource.REEL_PAGER, index: Int? = null) {
        tracker.observeScroll(ScrollObservation(time.nowMs, source, dy, dx, 1_000, index))
    }
    private fun changes() = tracker.snapshot(time.nowMs).changes

    @Test fun openingReplyBoxIsDirectDmEvidenceAndWinsOverBlockedScreenAndLink() {
        tracker.observe(InstagramObservation(time.nowMs, InstagramScreen.HOME_FEED))
        time.advanceBy(1)
        tracker.observeLink(LinkOpenedObservation(time.nowMs))
        time.advanceBy(1)
        tracker.observe(player(reply = true))
        assertEquals(ReelOrigin.DM, tracker.snapshot(time.nowMs).origin)
        assertTrue(tracker.snapshot(time.nowMs).originResolutionComplete)
    }

    @Test fun replyBoxAt599MsResolvesDm() {
        tracker.observe(player())
        time.advanceBy(599)
        tracker.observe(player(reply = true))
        assertEquals(ReelOrigin.DM, tracker.snapshot(time.nowMs).origin)
        assertEquals(0L, tracker.replyBoxMissingMs(time.nowMs))
    }

    @Test fun replyBoxAt600MsCannotResolveOriginAndTimerReports600() {
        tracker.observe(player())
        time.advanceBy(600)
        tracker.observe(player(reply = true))
        assertEquals(ReelOrigin.UNRESOLVED, tracker.snapshot(time.nowMs).origin)
        assertTrue(tracker.snapshot(time.nowMs).originResolutionComplete)
        assertEquals(600L, tracker.replyBoxMissingMs(time.nowMs))
    }

    @Test fun deadlineCanExpireWithoutAnotherObservationAndNeverReopens() {
        tracker.observe(player())
        time.advanceBy(600)
        assertTrue(tracker.snapshot(time.nowMs).originResolutionComplete)
        time.advanceBy(100)
        tracker.observe(player(reply = true))
        assertEquals(ReelOrigin.UNRESOLVED, tracker.snapshot(time.nowMs).origin)
    }

    @Test fun profileAdmissionAt1499And1501Ms() {
        for ((delay, expected) in listOf(1_499L to ReelOrigin.PROFILE, 1_501L to ReelOrigin.UNRESOLVED)) {
            val current = ReelSessionTracker(0.12)
            current.observe(InstagramObservation(0, InstagramScreen.PROFILE))
            current.observe(InstagramObservation(delay, InstagramScreen.REEL_PLAYER))
            assertEquals(expected, current.snapshot(delay).origin)
        }
    }

    @Test fun directlyOpenedReelsTabLatchesReelsTabOrigin() {
        tracker.observe(player().copy(reelsTabSelected = true))
        assertEquals(ReelOrigin.REELS_TAB, tracker.snapshot(time.nowMs).origin)
        assertTrue(tracker.snapshot(time.nowMs).originResolutionComplete)
    }

    @Test fun selectedReelsTabOverridesProfileSeen500MsEarlier() {
        tracker.observe(InstagramObservation(time.nowMs, InstagramScreen.PROFILE))
        time.advanceBy(500)
        tracker.observe(player().copy(reelsTabSelected = true))
        assertEquals(ReelOrigin.REELS_TAB, tracker.snapshot(time.nowMs).origin)
    }

    @Test fun openingReplyBoxStillWinsOverSelectedReelsTab() {
        tracker.observe(player(reply = true).copy(reelsTabSelected = true))
        assertEquals(ReelOrigin.DM, tracker.snapshot(time.nowMs).origin)
    }

    @Test fun storyUses1500MsAdmissionAndRejectsSelectedReelsTab() {
        for ((delay, selected, expected) in listOf(
            Triple(1_500L, false, ReelOrigin.STORY),
            Triple(1_501L, false, ReelOrigin.UNRESOLVED),
            Triple(100L, true, ReelOrigin.REELS_TAB),
        )) {
            val current = ReelSessionTracker(0.12)
            current.observe(InstagramObservation(0, InstagramScreen.STORY_VIEWER))
            current.observe(InstagramObservation(delay, InstagramScreen.REEL_PLAYER, reelsTabSelected = selected))
            assertEquals(expected, current.snapshot(delay).origin)
        }
    }

    @Test fun linkUsesOnlyFiveSecondWindowIncludingColdStart() {
        for ((delay, expected) in listOf(4_999L to ReelOrigin.LINK, 5_000L to ReelOrigin.LINK,
            5_001L to ReelOrigin.UNRESOLVED)) {
            val current = ReelSessionTracker(0.12)
            current.observeLink(LinkOpenedObservation(0))
            current.observe(InstagramObservation(delay, InstagramScreen.REEL_PLAYER, usageAccessGranted = true))
            val snapshot = current.snapshot(delay)
            assertEquals(expected, snapshot.origin)
            if (expected == ReelOrigin.LINK) {
                assertEquals(delay, snapshot.originAgeAtOpenMs)
                assertEquals(listOf(Action.Allow), InstagramPolicy().decide(InstagramSnapshot(
                    InstagramScreen.REEL_PLAYER, reel = snapshot)).actions)
            }
        }
    }

    @Test fun linkWithoutUsageAccessIsUnresolvedRatherThanOlderProfile() {
        tracker.observe(InstagramObservation(time.nowMs, InstagramScreen.PROFILE))
        time.advanceBy(10)
        tracker.observeLink(LinkOpenedObservation(time.nowMs))
        time.advanceBy(10)
        tracker.observe(player())
        assertEquals(ReelOrigin.UNRESOLVED, tracker.snapshot(time.nowMs).origin)
    }

    @Test fun mostRecentLinkOverridesHomeAndMostRecentScreenOverridesLink() {
        tracker.observe(InstagramObservation(0, InstagramScreen.HOME_FEED))
        tracker.observeLink(LinkOpenedObservation(100))
        tracker.observe(InstagramObservation(200, InstagramScreen.REEL_PLAYER, usageAccessGranted = true))
        assertEquals(ReelOrigin.LINK, tracker.snapshot(200).origin)
        val other = ReelSessionTracker(0.12)
        other.observeLink(LinkOpenedObservation(0))
        other.observe(InstagramObservation(100, InstagramScreen.PROFILE))
        other.observe(InstagramObservation(200, InstagramScreen.REEL_PLAYER, usageAccessGranted = true))
        assertEquals(ReelOrigin.PROFILE, other.snapshot(200).origin)
    }

    @Test fun recognisedBlockedOriginsLatchImmediatelyAndCannotUpgradeLater() {
        for ((screen, origin) in listOf(
            InstagramScreen.HOME_FEED to ReelOrigin.HOME_FEED, InstagramScreen.EXPLORE to ReelOrigin.EXPLORE,
            InstagramScreen.SEARCH_RESULTS to ReelOrigin.SEARCH_RESULTS, InstagramScreen.FAVORITES to ReelOrigin.FAVORITES,
            InstagramScreen.REELS_TAB to ReelOrigin.REELS_TAB,
        )) {
            val current = ReelSessionTracker(0.12)
            current.observe(InstagramObservation(0, screen))
            current.observe(InstagramObservation(10, InstagramScreen.REEL_PLAYER))
            assertEquals(origin, current.snapshot(10).origin)
            current.observe(InstagramObservation(599, InstagramScreen.REEL_PLAYER, replyBoxVisible = true))
            assertEquals(origin, current.snapshot(599).origin)
        }
    }

    @Test fun enteringDmChatSupersedesOldHomeAndCanWaitForReplyBox() {
        tracker.observe(InstagramObservation(0, InstagramScreen.HOME_FEED))
        tracker.observe(InstagramObservation(100, InstagramScreen.DM_CHAT))
        tracker.observe(InstagramObservation(200, InstagramScreen.REEL_PLAYER))
        assertEquals(ReelOrigin.UNRESOLVED, tracker.snapshot(200).origin)
        tracker.observe(InstagramObservation(799, InstagramScreen.REEL_PLAYER, replyBoxVisible = true))
        assertEquals(ReelOrigin.DM, tracker.snapshot(799).origin)
    }

    @Test fun blockedOriginsDoNotUseProfileOrLinkAdmissionWindows() {
        tracker.observe(InstagramObservation(0, InstagramScreen.HOME_FEED))
        tracker.observe(InstagramObservation(10_000, InstagramScreen.REEL_PLAYER))
        assertEquals(ReelOrigin.HOME_FEED, tracker.snapshot(10_000).origin)
        assertEquals(listOf(Action.Back), InstagramPolicy().decide(InstagramSnapshot(
            InstagramScreen.REEL_PLAYER, reel = tracker.snapshot(10_000))).actions)
    }

    @Test fun establishedOriginAndTimerSurviveReplyBoxDisappearance() {
        tracker.observe(player(reply = true))
        time.advanceBy(2_000)
        tracker.observe(player(reply = false))
        assertEquals(ReelOrigin.DM, tracker.snapshot(time.nowMs).origin)
        assertEquals(0L, tracker.replyBoxMissingMs(time.nowMs))
    }

    @Test fun commentsNeverProduceEvidenceEvenWhenLargeAndReportingAnIndex() {
        tracker.observe(player(reply = true))
        scroll(200, source = SwipeSource.OTHER_CONTENT, index = 4)
        assertTrue(changes().isEmpty())
    }

    @Test fun sheetKeepsSessionAndDoesNotReplacePagerBaselines() {
        tracker.observe(player(reply = true))
        time.advanceBy(100)
        tracker.observe(player(index = 8).copy(sheetOrMenuOpen = true))
        scroll(500, index = 8)
        time.advanceBy(2_000)
        tracker.observe(player(index = 0))
        assertEquals(ReelOrigin.DM, tracker.snapshot(time.nowMs).origin)
        assertTrue(changes().isEmpty())
        time.advanceBy(1)
        tracker.observe(player(index = 1))
        assertEquals(ReelChange.PAGE_INDEX, changes().single().change)
    }

    @Test fun pageIndexChangesAndDuplicateScreenScrollReportsAreDeduplicated() {
        tracker.observe(player(reply = true))
        time.advanceBy(100)
        tracker.observe(player(index = 1))
        scroll(0, index = 1)
        assertEquals(1, changes().size)
        assertEquals(100L, changes().single().msSincePlayerOpened)
        scroll(0, index = 2)
        assertEquals(2, changes().size)
    }

    @Test fun elevenPointNinePercentDoesNotSwipeButTwelveDoes() {
        tracker.observe(player(reply = true))
        scroll(119)
        assertTrue(changes().isEmpty())
        scroll(1)
        assertEquals(ReelChange.VERTICAL_SCROLL_THRESHOLD, changes().single().change)
    }

    @Test fun twoSixPercentScrollsWithinOneSecondAccumulate() {
        tracker.observe(player(reply = true))
        scroll(60)
        time.advanceBy(1_000)
        scroll(60)
        assertEquals(ReelChange.VERTICAL_SCROLL_THRESHOLD, changes().single().change)
    }

    @Test fun onePointOneSecondGapResetsScrollAccumulator() {
        tracker.observe(player(reply = true))
        scroll(60)
        time.advanceBy(1_100)
        scroll(60)
        assertTrue(changes().isEmpty())
    }

    @Test fun netScrollJiggleDoesNotSwipe() {
        tracker.observe(player(reply = true))
        for (dy in listOf(60, -50, 60)) { scroll(dy); time.advanceBy(100) }
        assertTrue(changes().isEmpty())
    }

    @Test fun negativeNetScrollCanCrossThreshold() {
        tracker.observe(player(reply = true))
        scroll(-60)
        scroll(-60)
        assertEquals(ReelChange.VERTICAL_SCROLL_THRESHOLD, changes().single().change)
    }

    @Test fun horizontalDominantEventsAreIgnoredIncludingTheirIndex() {
        tracker.observe(player(reply = true))
        scroll(200, dx = 201, index = 2)
        assertTrue(changes().isEmpty())
    }

    @Test fun equalDiagonalComponentsCountAsVertical() {
        tracker.observe(player(reply = true))
        scroll(60, dx = 60)
        scroll(60, dx = -60)
        assertEquals(ReelChange.VERTICAL_SCROLL_THRESHOLD, changes().single().change)
    }

    @Test fun invalidHeightCannotCreateScrollThresholdEvidence() {
        tracker.observe(player(reply = true))
        tracker.observeScroll(ScrollObservation(time.nowMs, SwipeSource.REEL_PAGER, 100, 0, 0))
        assertTrue(changes().isEmpty())
    }

    @Test fun authorChangesOnlyWhenExactlyOneLabelIsVisible() {
        val first = AuthorKey("opaque-1")
        val second = AuthorKey("opaque-2")
        tracker.observe(player(reply = true).copy(authorKeys = listOf(first)))
        tracker.observe(player().copy(authorKeys = listOf(second, second)))
        tracker.observe(player().copy(authorKeys = emptyList()))
        assertTrue(changes().isEmpty())
        tracker.observe(player().copy(authorKeys = listOf(second)))
        assertEquals(ReelChange.AUTHOR, changes().single().change)
    }

    @Test fun tabSelectionProducesEvidenceOnlyOnTransition() {
        tracker.observe(player(reply = true))
        tracker.observe(player().copy(reelsTabSelected = true))
        tracker.observe(player().copy(reelsTabSelected = true))
        assertEquals(ReelChange.REELS_TAB_SELECTED, changes().single().change)
    }

    @Test fun absenceOf1499MsKeepsSessionBut1500MsResetsIt() {
        for ((gap, expected) in listOf(1_499L to ReelOrigin.DM, 1_500L to ReelOrigin.UNRESOLVED)) {
            val current = ReelSessionTracker(0.12)
            current.observe(InstagramObservation(0, InstagramScreen.REEL_PLAYER, replyBoxVisible = true, pageIndex = 0))
            current.observe(InstagramObservation(10, InstagramScreen.REEL_PLAYER, pageIndex = 1))
            current.observe(InstagramObservation(20, InstagramScreen.UNKNOWN))
            current.observe(InstagramObservation(20 + gap, InstagramScreen.REEL_PLAYER, pageIndex = 0))
            assertEquals(expected, current.snapshot(20 + gap).origin)
            assertEquals(gap == 1_500L, current.snapshot(20 + gap).changes.isEmpty())
        }
    }

    @Test fun absenceCanExpireOnSnapshotRead() {
        tracker.observe(player(reply = true))
        tracker.observe(InstagramObservation(time.nowMs, InstagramScreen.UNKNOWN))
        time.advanceBy(1_500)
        assertEquals(ReelSessionSnapshot(), tracker.snapshot(time.nowMs))
    }

    @Test fun evidenceStaysBoundedDuringLongSessions() {
        tracker.observe(player(reply = true).copy(authorKeys = listOf(AuthorKey("opaque-0"))))
        for (index in 1..20) {
            time.advanceBy(100)
            tracker.observe(player(index = index).copy(authorKeys = listOf(AuthorKey("opaque-$index"))))
            scroll(120)
        }
        assertEquals(2, changes().count { it.change == ReelChange.PAGE_INDEX })
        assertEquals(2, changes().count { it.change == ReelChange.AUTHOR })
        assertEquals(1, changes().count { it.change == ReelChange.VERTICAL_SCROLL_THRESHOLD })
        assertFalse(AuthorKey("opaque-value").toString().contains("opaque-value"))
    }
}
