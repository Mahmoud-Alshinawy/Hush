package app.hushfocus.hush.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstagramCoversPolicyTest {
    private val policy = InstagramPolicy()
    private val rect = CoverRect(0, 100, 400, 700)
    private val tabRect = CoverRect(200, 700, 250, 800)

    @Test fun hiddenFeedShowsPostsCoverAndRequestsAudioFocus() {
        assertEquals(listOf(Action.ShowCover(CoverKind.FEED, rect), Action.RequestAudioFocus),
            policy.decide(InstagramSnapshot(
                InstagramScreen.HOME_FEED,
                settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN),
                coverBounds = mapOf(CoverKind.FEED to rect),
            )).actions)
    }

    @Test fun normalFeedIsUntouchedBeforeCaughtUpRow() {
        assertTrue(policy.decide(InstagramSnapshot(InstagramScreen.HOME_FEED,
            coverBounds = mapOf(CoverKind.FEED to rect, CoverKind.CAUGHT_UP to rect))).actions.isEmpty())
    }

    @Test fun caughtUpThresholdVerdictShowsFullPanelAndAudioFocus() {
        assertEquals(listOf(Action.ShowCover(CoverKind.CAUGHT_UP, rect), Action.RequestAudioFocus),
            policy.decide(InstagramSnapshot(InstagramScreen.HOME_FEED, caughtUpRowReached = true,
                coverBounds = mapOf(CoverKind.CAUGHT_UP to rect))).actions)
    }

    @Test fun disablingCaughtUpOrHidingFeedPreventsCaughtUpCover() {
        for (settings in listOf(InstagramSettings(stopAtCaughtUp = false),
            InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN))) {
            assertFalse(policy.decide(InstagramSnapshot(InstagramScreen.HOME_FEED,
                settings = settings, caughtUpRowReached = true,
                coverBounds = mapOf(CoverKind.CAUGHT_UP to rect))).actions.any {
                it is Action.ShowCover && it.kind == CoverKind.CAUGHT_UP
            })
        }
    }

    @Test fun scrollingAboveCaughtUpRowRemovesPanelAndReleasesAudioFocus() {
        assertEquals(listOf(Action.HideCover(CoverKind.CAUGHT_UP), Action.ReleaseAudioFocus),
            policy.decide(InstagramSnapshot(InstagramScreen.HOME_FEED,
                visibleCovers = mapOf(CoverKind.CAUGHT_UP to rect), audioFocusHeld = true,
                coverBounds = mapOf(CoverKind.CAUGHT_UP to rect))).actions)
    }

    @Test fun followingAndFavoritesHaveNoFeedPanelAndHomeRestoresIt() {
        val settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN)
        for (screen in listOf(InstagramScreen.FOLLOWING, InstagramScreen.FAVORITES)) {
            assertEquals(listOf(Action.HideCover(CoverKind.FEED), Action.ReleaseAudioFocus),
                policy.decide(InstagramSnapshot(screen, settings = settings,
                    coverBounds = mapOf(CoverKind.FEED to rect),
                    visibleCovers = mapOf(CoverKind.FEED to rect), audioFocusHeld = true)).actions)
        }
        assertTrue(Action.ShowCover(CoverKind.FEED, rect) in policy.decide(InstagramSnapshot(
            InstagramScreen.HOME_FEED, settings = settings, coverBounds = mapOf(CoverKind.FEED to rect),
        )).actions)
    }

    @Test fun exploreCoverOnlyAppliesToSuggestedGridWhenEnabled() {
        for (screen in listOf(InstagramScreen.EXPLORE, InstagramScreen.SEARCH_RESULTS, InstagramScreen.PROFILE)) {
            val actions = policy.decide(InstagramSnapshot(screen,
                settings = InstagramSettings(hideExplore = true),
                coverBounds = mapOf(CoverKind.EXPLORE to rect))).actions
            assertEquals(screen == InstagramScreen.EXPLORE, Action.ShowCover(CoverKind.EXPLORE, rect) in actions)
        }
        assertTrue(policy.decide(InstagramSnapshot(InstagramScreen.EXPLORE,
            coverBounds = mapOf(CoverKind.EXPLORE to rect))).actions.isEmpty())
    }

    @Test fun storiesRowIsCoveredOnlyWhenHiddenOnHome() {
        val snapshot = InstagramSnapshot(InstagramScreen.HOME_FEED,
            settings = InstagramSettings(hideStories = true), coverBounds = mapOf(CoverKind.STORIES_ROW to rect))
        assertEquals(listOf(Action.ShowCover(CoverKind.STORIES_ROW, rect)), policy.decide(snapshot).actions)
        assertTrue(policy.decide(snapshot.copy(settings = InstagramSettings())).actions.isEmpty())
    }

    @Test fun hiddenStoryViewerHasCoverAndAudioFocusWithoutBack() {
        val actions = policy.decide(InstagramSnapshot(InstagramScreen.STORY_VIEWER,
            settings = InstagramSettings(hideStories = true), coverBounds = mapOf(CoverKind.STORY_VIEWER to rect))).actions
        assertEquals(listOf(Action.ShowCover(CoverKind.STORY_VIEWER, rect), Action.RequestAudioFocus), actions)
        assertFalse(Action.Back in actions)
    }

    @Test fun hiddenStoryViewerCloseButtonUsesBack() {
        val actions = policy.decide(InstagramSnapshot(InstagramScreen.STORY_VIEWER,
            settings = InstagramSettings(hideStories = true), coverBounds = mapOf(CoverKind.STORY_VIEWER to rect),
            interaction = InstagramInteraction.CLOSE_STORY)).actions
        assertTrue(Action.Back in actions)
    }

    @Test fun validatedReelsTabBoundsAreUsedExactly() {
        assertEquals(listOf(Action.ShowCover(CoverKind.REELS_TAB, tabRect)), policy.decide(InstagramSnapshot(
            InstagramScreen.HOME_FEED, coverBounds = mapOf(CoverKind.REELS_TAB to tabRect),
        )).actions)
    }

    @Test fun absentOrRejectedCoverTargetNeverProducesGuessedCover() {
        for (screen in listOf(InstagramScreen.HOME_FEED, InstagramScreen.EXPLORE, InstagramScreen.STORY_VIEWER)) {
            val actions = policy.decide(InstagramSnapshot(screen,
                settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN,
                    hideStories = true, hideExplore = true))).actions
            assertTrue(actions.none { it is Action.ShowCover })
            assertFalse(Action.RequestAudioFocus in actions)
        }
    }

    @Test fun everyReelsTabSuppressionConditionRemovesItsCover() {
        val base = InstagramSnapshot(InstagramScreen.HOME_FEED,
            coverBounds = mapOf(CoverKind.REELS_TAB to tabRect),
            visibleCovers = mapOf(CoverKind.REELS_TAB to tabRect))
        for (snapshot in listOf(
            base.copy(sheetOrMenuOpen = true),
            base.copy(screen = InstagramScreen.OVERLAY_UI, sheetOrMenuOpen = true),
            base.copy(backArrowVisible = true), base.copy(dmComposerVisible = true),
            base.copy(storyViewerVisible = true), base.copy(reelPlayerVisible = true,
                reel = ReelSessionSnapshot(origin = ReelOrigin.DM)),
            base.copy(replyBoxVisible = true), base.copy(allowance = AllowanceState(remainingMs = 1)),
        )) {
            val actions = policy.decide(snapshot).actions
            assertTrue(Action.HideCover(CoverKind.REELS_TAB) in actions)
            assertFalse(actions.any { it is Action.ShowCover && it.kind == CoverKind.REELS_TAB })
        }
    }

    @Test fun missingReelsTabTargetRemovesOldCover() {
        assertEquals(listOf(Action.HideCover(CoverKind.REELS_TAB)), policy.decide(InstagramSnapshot(
            InstagramScreen.HOME_FEED, visibleCovers = mapOf(CoverKind.REELS_TAB to tabRect),
        )).actions)
    }

    @Test fun reelsCoverTapUsesMessageId() {
        assertTrue(Action.ShowMessage(HushMessage.REELS_BLOCKED) in policy.decide(InstagramSnapshot(
            InstagramScreen.HOME_FEED, coverBounds = mapOf(CoverKind.REELS_TAB to tabRect),
            interaction = InstagramInteraction.REELS_COVER_TAPPED,
        )).actions)
    }

    @Test fun unchangedCoverAndHeldFocusNeedNoRepeatedActions() {
        assertTrue(policy.decide(InstagramSnapshot(InstagramScreen.HOME_FEED,
            settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN),
            coverBounds = mapOf(CoverKind.FEED to rect), visibleCovers = mapOf(CoverKind.FEED to rect),
            audioFocusHeld = true)).actions.isEmpty())
    }

    @Test fun changedValidatedBoundsRelayoutCover() {
        val moved = rect.copy(bottom = 650)
        assertEquals(listOf(Action.ShowCover(CoverKind.FEED, moved)), policy.decide(InstagramSnapshot(
            InstagramScreen.HOME_FEED, settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN),
            coverBounds = mapOf(CoverKind.FEED to moved), visibleCovers = mapOf(CoverKind.FEED to rect),
            audioFocusHeld = true,
        )).actions)
    }

    @Test fun dmsOnlyRedirectsWithoutAnyCoversAndClearsOldOnes() {
        val allCovers = CoverKind.entries.associateWith { rect }
        val actions = policy.decide(InstagramSnapshot(InstagramScreen.HOME_FEED,
            settings = InstagramSettings(dmsOnly = true, homeFeedMode = HomeFeedMode.HIDDEN,
                hideExplore = true, hideStories = true),
            tabBarVisible = true, targets = setOf(ActionTarget.DMS_TAB), caughtUpRowReached = true,
            coverBounds = allCovers, visibleCovers = allCovers, audioFocusHeld = true)).actions
        assertTrue(Action.Tap(ActionTarget.DMS_TAB) in actions)
        assertTrue(Action.ReleaseAudioFocus in actions)
        for (kind in CoverKind.entries) assertTrue(Action.HideCover(kind) in actions)
        assertTrue(actions.none { it is Action.ShowCover || it == Action.Back })
    }

    @Test fun dmsOnlyMissingOrFailedDmsTargetNeverBacks() {
        val snapshot = InstagramSnapshot(InstagramScreen.REELS_TAB,
            settings = InstagramSettings(dmsOnly = true), tabBarVisible = true)
        assertTrue(policy.decide(snapshot).actions.isEmpty())
        assertTrue(policy.decide(snapshot.copy(targets = setOf(ActionTarget.DMS_TAB),
            failedTap = ActionTarget.DMS_TAB)).actions.isEmpty())
    }

    @Test fun dmsOnlyDoesNotRedirectDmScreensOrWithoutTabBar() {
        for (screen in listOf(InstagramScreen.DM_INBOX, InstagramScreen.DM_CHAT, InstagramScreen.HOME_FEED)) {
            assertTrue(policy.decide(InstagramSnapshot(screen, settings = InstagramSettings(dmsOnly = true),
                tabBarVisible = screen != InstagramScreen.HOME_FEED,
                targets = setOf(ActionTarget.DMS_TAB))).actions.isEmpty())
        }
    }

    @Test fun dmsOnlyStillBacksAfterPagerSwipeInDmReel() {
        assertEquals(listOf(Action.Back), policy.decide(InstagramSnapshot(InstagramScreen.REEL_PLAYER,
            settings = InstagramSettings(dmsOnly = true), tabBarVisible = true,
            targets = setOf(ActionTarget.DMS_TAB), reel = ReelSessionSnapshot(origin = ReelOrigin.DM,
                changes = listOf(ReelSwipeEvidence(SwipeSource.REEL_PAGER, ReelChange.PAGE_INDEX, 2_000))),
        )).actions)
    }

    @Test fun dmsOnlyRejectsProfileReelAndStillRedirectsWithAllowance() {
        val snapshot = InstagramSnapshot(InstagramScreen.REEL_PLAYER,
            settings = InstagramSettings(dmsOnly = true), reel = ReelSessionSnapshot(
                origin = ReelOrigin.PROFILE, originAgeAtOpenMs = 100, msFromOriginToPlayer = 100))
        assertEquals(listOf(Action.Back), policy.decide(snapshot).actions)
        assertEquals(listOf(Action.Tap(ActionTarget.DMS_TAB)), policy.decide(snapshot.copy(
            allowance = AllowanceState(remainingMs = 1), tabBarVisible = true,
            targets = setOf(ActionTarget.DMS_TAB))).actions)
    }

    @Test fun allowanceDoesNotRemoveFeedExploreOrStoryProtection() {
        val settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN, hideExplore = true, hideStories = true)
        for ((screen, kind) in listOf(InstagramScreen.HOME_FEED to CoverKind.FEED,
            InstagramScreen.EXPLORE to CoverKind.EXPLORE, InstagramScreen.STORY_VIEWER to CoverKind.STORY_VIEWER)) {
            assertTrue(Action.ShowCover(kind, rect) in policy.decide(InstagramSnapshot(screen,
                settings = settings, allowance = AllowanceState(remainingMs = 60_000),
                coverBounds = mapOf(kind to rect))).actions)
        }
    }
}
