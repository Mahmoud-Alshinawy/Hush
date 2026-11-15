package app.hushfocus.hush.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalPolicyTest {
    private val policy = GlobalPolicy()
    private val blockedReel = InstagramSnapshot(
        screen = InstagramScreen.REEL_PLAYER,
        reel = ReelSessionSnapshot(origin = ReelOrigin.HOME_FEED),
    )

    private fun assertBlock(controls: GlobalControlState, reason: BlockReason) {
        val decision = policy.decide(PolicySnapshot(controls = controls, instagram = blockedReel))
        assertEquals(listOf(
            Action.HideAllCovers, Action.ReleaseAudioFocus, Action.ShowBlockScreen(reason),
        ), decision.actions)
        assertEquals(null, decision.recheckAfterMs)
    }

    @Test fun sleepBeatsDowntimeAndEveryLowerPriorityControl() {
        assertBlock(GlobalControlState(true, true, true, true), BlockReason.SLEEP)
    }

    @Test fun downtimeBeatsDailyLimitAndPause() {
        assertBlock(GlobalControlState(downtimeBlocksApp = true,
            dailyAppLimitReached = true, pauseActive = true), BlockReason.DOWNTIME)
    }

    @Test fun dailyLimitBeatsPause() {
        assertBlock(GlobalControlState(dailyAppLimitReached = true, pauseActive = true), BlockReason.DAILY_APP_LIMIT)
    }

    @Test fun pauseBeatsInstagramRulesAndClearsCoversAndAudio() {
        assertEquals(listOf(Action.HideAllCovers, Action.ReleaseAudioFocus), policy.decide(
            PolicySnapshot(controls = GlobalControlState(pauseActive = true), instagram = blockedReel),
        ).actions)
    }

    @Test fun sleepDowntimeAndDailyLimitStartingDoNotLeakInstagramActions() {
        val rect = CoverRect(0, 100, 400, 800)
        val instagram = InstagramSnapshot(
            screen = InstagramScreen.HOME_FEED,
            settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN),
            coverBounds = mapOf(CoverKind.FEED to rect),
            visibleCovers = mapOf(CoverKind.FEED to rect),
            audioFocusHeld = true,
            interaction = InstagramInteraction.OPEN_SEARCH,
            targets = setOf(ActionTarget.SEARCH_TAB),
        )
        assertTrue(policy.decide(PolicySnapshot(instagram = instagram)).actions.any { it is Action.Tap })
        for (controls in listOf(
            GlobalControlState(sleepBlocksApp = true),
            GlobalControlState(downtimeBlocksApp = true),
            GlobalControlState(dailyAppLimitReached = true),
            GlobalControlState(pauseActive = true),
        )) {
            val actions = policy.decide(PolicySnapshot(controls = controls, instagram = instagram)).actions
            assertTrue(Action.HideAllCovers in actions)
            assertTrue(Action.ReleaseAudioFocus in actions)
            assertTrue(actions.all {
                it == Action.HideAllCovers || it == Action.ReleaseAudioFocus || it is Action.ShowBlockScreen
            })
        }
    }

    @Test fun endingBlockOrPauseRestoresCurrentInstagramDecision() {
        for (controls in listOf(GlobalControlState(sleepBlocksApp = true),
            GlobalControlState(downtimeBlocksApp = true), GlobalControlState(dailyAppLimitReached = true),
            GlobalControlState(pauseActive = true))) {
            policy.decide(PolicySnapshot(controls = controls, instagram = blockedReel))
            assertEquals(listOf(Action.Back), policy.decide(PolicySnapshot(instagram = blockedReel)).actions)
        }
    }

    @Test fun hushOffBeatsEveryControlAndReturnsNothing() {
        val decision = policy.decide(PolicySnapshot(
            settings = GlobalSettings(hushEnabled = false),
            controls = GlobalControlState(true, true, true, true),
            instagram = blockedReel.copy(audioFocusHeld = true),
        ))
        assertEquals(PolicyDecision(), decision)
    }

    @Test fun switchingHushOnUsesFreshInputs() {
        val snapshot = PolicySnapshot(settings = GlobalSettings(false), instagram = blockedReel)
        assertEquals(PolicyDecision(), policy.decide(snapshot))
        assertEquals(listOf(Action.Back), policy.decide(snapshot.copy(settings = GlobalSettings(true))).actions)
    }

    @Test fun instagramFeedRulesBeatAllowance() {
        val rect = CoverRect(0, 100, 400, 800)
        val decision = policy.decide(PolicySnapshot(instagram = InstagramSnapshot(
            screen = InstagramScreen.HOME_FEED,
            settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN),
            allowance = AllowanceState(remainingMs = 60_000),
            coverBounds = mapOf(CoverKind.FEED to rect),
        )))
        assertTrue(Action.ShowCover(CoverKind.FEED, rect) in decision.actions)
    }

    @Test fun allowanceCannotBypassSleepDowntimeOrDailyLimit() {
        for (controls in listOf(GlobalControlState(sleepBlocksApp = true),
            GlobalControlState(downtimeBlocksApp = true), GlobalControlState(dailyAppLimitReached = true))) {
            val decision = policy.decide(PolicySnapshot(controls = controls,
                instagram = blockedReel.copy(allowance = AllowanceState(remainingMs = 60_000))))
            assertTrue(decision.actions.any { it is Action.ShowBlockScreen })
        }
    }

    @Test fun cooldownSkipsBackAndRequestsRemainingDelay() {
        val decision = policy.decide(PolicySnapshot(instagram = blockedReel, msSinceLastNavigationAction = 999))
        assertTrue(decision.actions.isEmpty())
        assertEquals(1L, decision.recheckAfterMs)
    }

    @Test fun cooldownEndFreshCheckActs() {
        val snapshot = PolicySnapshot(instagram = blockedReel, msSinceLastNavigationAction = 999)
        policy.decide(snapshot)
        assertEquals(listOf(Action.Back), policy.decide(snapshot.copy(msSinceLastNavigationAction = 1_000)).actions)
    }

    @Test fun cooldownNeverReplaysStaleBack() {
        policy.decide(PolicySnapshot(instagram = blockedReel, msSinceLastNavigationAction = 100))
        assertEquals(PolicyDecision(), policy.decide(PolicySnapshot(
            instagram = InstagramSnapshot(InstagramScreen.DM_CHAT), msSinceLastNavigationAction = 1_000,
        )))
    }

    @Test fun suppressedTapRequestsOneCheckAtCooldownEndRatherThanEarlierWorkflowDeadline() {
        val decision = policy.decide(PolicySnapshot(msSinceLastNavigationAction = 100,
            instagram = InstagramSnapshot(InstagramScreen.HOME_FEED,
                targets = setOf(ActionTarget.INSTAGRAM_LOGO),
                feedNavigation = FeedNavigationSnapshot(FeedDestination.FOLLOWING,
                    FeedNavigationStep.LOGO_MENU, msSinceProgress = 2_400))))
        assertTrue(decision.actions.isEmpty())
        assertEquals(900L, decision.recheckAfterMs)
    }

    @Test fun cooldownAlsoSkipsTapAndFailedTapFallback() {
        val snapshot = InstagramSnapshot(InstagramScreen.REELS_TAB, targets = setOf(ActionTarget.DMS_TAB))
        for (instagram in listOf(snapshot, snapshot.copy(failedTap = ActionTarget.DMS_TAB))) {
            val decision = policy.decide(PolicySnapshot(instagram = instagram, msSinceLastNavigationAction = 400))
            assertEquals(emptyList(), decision.actions)
            assertEquals(600L, decision.recheckAfterMs)
        }
        assertEquals(listOf(Action.Back), policy.decide(PolicySnapshot(
            instagram = snapshot.copy(failedTap = ActionTarget.DMS_TAB), msSinceLastNavigationAction = 1_000,
        )).actions)
    }

    @Test fun cooldownDoesNotDelayCoverOrAudioActions() {
        val rect = CoverRect(0, 100, 400, 800)
        val decision = policy.decide(PolicySnapshot(msSinceLastNavigationAction = 0, instagram = InstagramSnapshot(
            screen = InstagramScreen.HOME_FEED,
            settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN),
            coverBounds = mapOf(CoverKind.FEED to rect),
            interaction = InstagramInteraction.OPEN_SEARCH,
            targets = setOf(ActionTarget.SEARCH_TAB),
        )))
        assertEquals(listOf(Action.ShowCover(CoverKind.FEED, rect), Action.RequestAudioFocus), decision.actions)
        assertEquals(1_000L, decision.recheckAfterMs)
    }

    @Test fun unknownScreenDoesNothingEvenWithTargetsAndOldCovers() {
        val rect = CoverRect(0, 100, 400, 800)
        assertEquals(PolicyDecision(), policy.decide(PolicySnapshot(instagram = InstagramSnapshot(
            screen = InstagramScreen.UNKNOWN,
            settings = InstagramSettings(dmsOnly = true, hideStories = true),
            targets = ActionTarget.entries.toSet(), tabBarVisible = true,
            coverBounds = mapOf(CoverKind.REELS_TAB to rect),
            visibleCovers = mapOf(CoverKind.REELS_TAB to rect), audioFocusHeld = true,
        ))))
    }

    @Test fun wholeAppBlocksDoNotDependOnInstagramRecognition() {
        assertBlock(GlobalControlState(sleepBlocksApp = true), BlockReason.SLEEP)
        val decision = policy.decide(PolicySnapshot(controls = GlobalControlState(sleepBlocksApp = true)))
        assertTrue(Action.ShowBlockScreen(BlockReason.SLEEP) in decision.actions)
    }

    @Test fun otherAppsHaveNoInAppPolicyYetButStillHonorGlobalControls() {
        for (app in AppId.entries.filter { it != AppId.INSTAGRAM }) {
            assertEquals(PolicyDecision(), policy.decide(PolicySnapshot(app = app, instagram = blockedReel)))
            assertTrue(Action.ShowBlockScreen(BlockReason.SLEEP) in policy.decide(PolicySnapshot(
                app = app, controls = GlobalControlState(sleepBlocksApp = true),
            )).actions)
        }
    }

    @Test fun decisionsNeverContainMoreThanOneBackOrTap() {
        for (screen in InstagramScreen.entries) for (origin in ReelOrigin.entries) {
            for (dmsOnly in listOf(false, true)) for (sheet in listOf(false, true)) {
                val decision = policy.decide(PolicySnapshot(instagram = InstagramSnapshot(
                    screen = screen, settings = InstagramSettings(dmsOnly = dmsOnly),
                    reel = ReelSessionSnapshot(origin = origin),
                    sheetOrMenuOpen = sheet, tabBarVisible = true, targets = ActionTarget.entries.toSet(),
                    interaction = InstagramInteraction.OPEN_SEARCH,
                )))
                assertTrue(decision.actions.count { it.isNavigation() } <= 1, "$screen $origin $dmsOnly $sheet")
            }
        }
    }

    @Test fun settingsDefaultsMatchSpec() {
        val settings = InstagramSettings()
        assertEquals(true, settings.allowReelsSentInDms)
        assertEquals(HomeFeedMode.NORMAL, settings.homeFeedMode)
        assertEquals(true, settings.stopAtCaughtUp)
        assertEquals(false, settings.hideExplore)
        assertEquals(false, settings.hideStories)
        assertEquals(false, settings.dmsOnly)
        assertEquals(0L, AllowanceState().remainingMs)
    }
}
