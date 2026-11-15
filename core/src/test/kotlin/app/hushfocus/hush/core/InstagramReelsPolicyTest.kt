package app.hushfocus.hush.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstagramReelsPolicyTest {
    private val policy = InstagramPolicy()

    private fun player(origin: ReelOrigin = ReelOrigin.DM): InstagramSnapshot = InstagramSnapshot(
        screen = InstagramScreen.REEL_PLAYER,
        reel = ReelSessionSnapshot(
            origin = origin,
            originAgeAtOpenMs = 500,
            msFromOriginToPlayer = 500,
            usageAccessGranted = true,
        ),
        replyBoxVisible = origin == ReelOrigin.DM,
        replyBoxMissingMs = 600,
    )

    private fun change(
        kind: ReelChange = ReelChange.PAGE_INDEX,
        atMs: Long = 2_000,
        source: SwipeSource = SwipeSource.REEL_PAGER,
    ) = ReelSwipeEvidence(source, kind, atMs)

    private fun withChanges(snapshot: InstagramSnapshot, vararg changes: ReelSwipeEvidence) =
        snapshot.copy(reel = snapshot.reel.copy(changes = changes.toList()))

    private fun assertAllowed(snapshot: InstagramSnapshot) {
        assertEquals(listOf(Action.Allow), policy.decide(snapshot).actions)
    }

    private fun assertBack(snapshot: InstagramSnapshot) {
        assertEquals(listOf(Action.Back), policy.decide(snapshot).actions)
    }

    @Test fun dmOriginAllowsOneReel() = assertAllowed(player())
    @Test fun profileOriginAllowsOneReel() = assertAllowed(player(ReelOrigin.PROFILE))
    @Test fun storyOriginAllowsOneReel() = assertAllowed(player(ReelOrigin.STORY))
    @Test fun linkOriginWithUsageAccessAllowsOneReel() = assertAllowed(player(ReelOrigin.LINK))
    @Test fun homeOriginBacksImmediately() = assertBack(player(ReelOrigin.HOME_FEED).copy(replyBoxMissingMs = 0))
    @Test fun exploreOriginBacksImmediately() = assertBack(player(ReelOrigin.EXPLORE).copy(replyBoxMissingMs = 0))
    @Test fun searchResultsOriginBacksImmediately() = assertBack(player(ReelOrigin.SEARCH_RESULTS).copy(replyBoxMissingMs = 0))
    @Test fun favoritesOriginBacksImmediately() = assertBack(player(ReelOrigin.FAVORITES).copy(replyBoxMissingMs = 0))
    @Test fun unresolvedOriginBacksAt600Ms() = assertBack(player(ReelOrigin.UNRESOLVED))

    @Test fun reelsTabTapsDms() {
        val snapshot = InstagramSnapshot(InstagramScreen.REELS_TAB, targets = setOf(ActionTarget.DMS_TAB))
        assertEquals(listOf(Action.Tap(ActionTarget.DMS_TAB)), policy.decide(snapshot).actions)
    }

    @Test fun dmReelsSettingOffBacksImmediately() {
        assertBack(player().copy(settings = InstagramSettings(allowReelsSentInDms = false)))
    }

    @Test fun hideStoriesDisablesStoryOrigin() {
        assertBack(player(ReelOrigin.STORY).copy(settings = InstagramSettings(hideStories = true)))
    }

    @Test fun selectedReelsTabDisablesStoryOrigin() {
        assertBack(player(ReelOrigin.STORY).copy(reelsTabSelected = true))
    }

    @Test fun missingUsageAccessMakesLinkOriginUnresolved() {
        val snapshot = player(ReelOrigin.LINK)
        assertBack(snapshot.copy(reel = snapshot.reel.copy(usageAccessGranted = false)))
    }

    @Test fun unresolvedOriginAt599MsAllowsAndRequestsFreshCheck() {
        val decision = policy.decide(player(ReelOrigin.UNRESOLVED).copy(replyBoxMissingMs = 599))
        assertEquals(listOf(Action.Allow), decision.actions)
        assertEquals(1L, decision.recheckAfterMs)
    }

    @Test fun unresolvedOriginWithLateReplyBoxUsesDmRule() {
        assertAllowed(player(ReelOrigin.UNRESOLVED).copy(replyBoxVisible = true))
        assertBack(player(ReelOrigin.UNRESOLVED).copy(
            replyBoxVisible = true,
            settings = InstagramSettings(allowReelsSentInDms = false),
        ))
    }

    @Test fun replyBoxDoesNotOverrideRecognisedBlockedOrigin() {
        assertBack(player(ReelOrigin.HOME_FEED).copy(replyBoxVisible = true, replyBoxMissingMs = 0))
    }

    @Test fun establishedDmOriginSurvivesReplyBoxHiddenForTwoSeconds() {
        assertAllowed(player().copy(replyBoxVisible = false, replyBoxMissingMs = 2_000))
    }

    @Test fun establishedProfileOriginIsNotReclassifiedAsDm() {
        assertAllowed(player(ReelOrigin.PROFILE).copy(
            replyBoxVisible = true,
            settings = InstagramSettings(allowReelsSentInDms = false),
        ))
    }

    @Test fun rememberedOriginAndOpeningBoundaryAreInclusive() {
        for (origin in listOf(ReelOrigin.PROFILE, ReelOrigin.STORY, ReelOrigin.LINK)) {
            val snapshot = player(origin)
            assertAllowed(snapshot.copy(reel = snapshot.reel.copy(
                originAgeAtOpenMs = 5_000, msFromOriginToPlayer = 1_500,
            )))
        }
    }

    @Test fun expiredRememberedOriginIsUnresolved() {
        for (origin in listOf(ReelOrigin.PROFILE, ReelOrigin.STORY, ReelOrigin.LINK)) {
            val snapshot = player(origin)
            assertBack(snapshot.copy(reel = snapshot.reel.copy(originAgeAtOpenMs = 5_001)))
        }
    }

    @Test fun openingTooLateOrWithoutEvidenceIsUnresolved() {
        val snapshot = player(ReelOrigin.PROFILE)
        assertBack(snapshot.copy(reel = snapshot.reel.copy(msFromOriginToPlayer = 1_501)))
        assertBack(snapshot.copy(reel = snapshot.reel.copy(originAgeAtOpenMs = null)))
        assertBack(snapshot.copy(reel = snapshot.reel.copy(msFromOriginToPlayer = null)))
    }

    @Test fun watchingBeyondFiveSecondsDoesNotExpireAnAdmittedReel() {
        val snapshot = player(ReelOrigin.PROFILE).copy(replyBoxMissingMs = 60_000)
        assertAllowed(snapshot)
        assertAllowed(snapshot)
    }

    @Test fun everyPagerSwipeSignalBacksOutOfDmReel() {
        for (kind in ReelChange.entries) assertBack(withChanges(player(), change(kind)))
    }

    @Test fun dmOriginHasNoLoadingGrace() {
        assertBack(withChanges(player(), change(atMs = 100)))
    }

    @Test fun profileStoryAndLinkTolerateOneEarlyIndexAndAuthorChange() {
        for (origin in listOf(ReelOrigin.PROFILE, ReelOrigin.STORY, ReelOrigin.LINK)) {
            assertAllowed(withChanges(player(origin),
                change(ReelChange.PAGE_INDEX, 100), change(ReelChange.AUTHOR, 1_499),
            ))
        }
    }

    @Test fun secondIndexOrAuthorChangeDuringGraceBacks() {
        for (kind in listOf(ReelChange.PAGE_INDEX, ReelChange.AUTHOR)) {
            assertBack(withChanges(player(ReelOrigin.PROFILE), change(kind, 100), change(kind, 200)))
        }
    }

    @Test fun changeAtGraceEndBacks() {
        for (kind in listOf(ReelChange.PAGE_INDEX, ReelChange.AUTHOR)) {
            assertBack(withChanges(player(ReelOrigin.PROFILE), change(kind, 1_500)))
        }
    }

    @Test fun scrollingOrSelectingReelsTabIsNotToleratedDuringGrace() {
        for (kind in listOf(ReelChange.VERTICAL_SCROLL_THRESHOLD, ReelChange.REELS_TAB_SELECTED)) {
            assertBack(withChanges(player(ReelOrigin.PROFILE), change(kind, 100)))
        }
    }

    @Test fun twentyPercentCommentListScrollDoesNotBack() {
        // Even a 20% comment scroll crossing the pager threshold is evidence from other content.
        assertAllowed(withChanges(player(), change(
            ReelChange.VERTICAL_SCROLL_THRESHOLD, source = SwipeSource.OTHER_CONTENT,
        )))
    }

    @Test fun captionAndShareSheetChangesCannotBecomePagerEvidence() {
        for (kind in ReelChange.entries) {
            assertAllowed(withChanges(player(), change(kind, source = SwipeSource.OTHER_CONTENT)))
        }
    }

    @Test fun sheetOverDmReelDoesNothingAndClosingKeepsItAllowed() {
        val snapshot = player()
        assertTrue(policy.decide(snapshot.copy(sheetOrMenuOpen = true)).actions.isEmpty())
        assertAllowed(snapshot.copy(sheetOrMenuOpen = false))
    }

    @Test fun sheetOverPlayerRemovesExistingReelsTabCoverWithoutNavigation() {
        val rect = CoverRect(0, 700, 100, 800)
        val snapshot = withChanges(player(), change()).copy(
            screen = InstagramScreen.OVERLAY_UI,
            sheetOrMenuOpen = true,
            coverBounds = mapOf(CoverKind.REELS_TAB to rect),
            visibleCovers = mapOf(CoverKind.REELS_TAB to rect),
        )
        assertEquals(listOf(Action.HideCover(CoverKind.REELS_TAB)), policy.decide(snapshot).actions)
    }

    @Test fun sheetSuppressesBlockedOriginsAndReleasesObsoleteAudioFocus() {
        val decision = policy.decide(player(ReelOrigin.HOME_FEED).copy(
            sheetOrMenuOpen = true, audioFocusHeld = true,
        ))
        assertEquals(listOf(Action.ReleaseAudioFocus), decision.actions)
    }

    @Test fun sheetOverDmReelAlsoSuppressesDmsOnlyRedirect() {
        val snapshot = player().copy(settings = InstagramSettings(dmsOnly = true),
            tabBarVisible = true, targets = setOf(ActionTarget.DMS_TAB), sheetOrMenuOpen = true)
        assertTrue(policy.decide(snapshot).actions.isEmpty())
    }

    @Test fun actualPagerSwipeAfterSheetClosesBacks() {
        val snapshot = withChanges(player(), change())
        assertTrue(policy.decide(snapshot.copy(sheetOrMenuOpen = true)).actions.isEmpty())
        assertBack(snapshot.copy(sheetOrMenuOpen = false))
    }

    @Test fun suppliedNewSessionHasNoOldSwipeHistory() {
        assertBack(withChanges(player(), change()))
        // The navigation tracker supplies this fresh session after a 1.5 s absence.
        assertAllowed(player())
    }

    @Test fun missingDmsTabFallsBackToBack() {
        assertEquals(listOf(Action.Back), policy.decide(InstagramSnapshot(InstagramScreen.REELS_TAB)).actions)
    }

    @Test fun failedDmsTapFallsBackOnNextSnapshot() {
        assertEquals(listOf(Action.Back), policy.decide(InstagramSnapshot(
            InstagramScreen.REELS_TAB,
            targets = setOf(ActionTarget.DMS_TAB),
            failedTap = ActionTarget.DMS_TAB,
        )).actions)
    }

    @Test fun playerOpenedFromReelsTabRedirectsToDms() {
        assertEquals(listOf(Action.Tap(ActionTarget.DMS_TAB)), policy.decide(
            player(ReelOrigin.REELS_TAB).copy(targets = setOf(ActionTarget.DMS_TAB)),
        ).actions)
    }

    @Test fun allowanceSkipsEveryReelOriginAndSwipeRestriction() {
        for (origin in ReelOrigin.entries) {
            val snapshot = withChanges(player(origin), change()).copy(
                settings = InstagramSettings(allowReelsSentInDms = false, hideStories = true),
                allowance = AllowanceState(remainingMs = 1),
            )
            assertAllowed(snapshot)
        }
    }

    @Test fun allowanceUnlocksReelsTabAndExhaustionBlocksWithEnumMessage() {
        val snapshot = InstagramSnapshot(InstagramScreen.REELS_TAB)
        assertEquals(listOf(Action.Allow), policy.decide(snapshot.copy(
            allowance = AllowanceState(remainingMs = 1),
        )).actions)
        val exhausted = policy.decide(snapshot.copy(allowance = AllowanceState(justExhausted = true)))
        assertEquals(listOf(Action.Back, Action.ShowMessage(HushMessage.ALLOWANCE_USED_UP)), exhausted.actions)
    }

    @Test fun ordinaryRecognisedScreensDoNotTriggerReelBlocking() {
        for (screen in listOf(InstagramScreen.DM_CHAT, InstagramScreen.DM_INBOX,
                InstagramScreen.PROFILE, InstagramScreen.SEARCH_RESULTS, InstagramScreen.FOLLOWING,
                InstagramScreen.FAVORITES, InstagramScreen.STORY_VIEWER, InstagramScreen.HOME_FEED)) {
            assertFalse(policy.decide(InstagramSnapshot(screen)).actions.any { it.isNavigation() })
        }
    }
}
