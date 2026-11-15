package app.hushfocus.hush.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstagramFeedNavigationPolicyTest {
    private val policy = InstagramPolicy()
    private val rect = CoverRect(0, 100, 400, 700)

    private fun home(interaction: InstagramInteraction? = null) = InstagramSnapshot(
        screen = InstagramScreen.HOME_FEED,
        settings = InstagramSettings(homeFeedMode = HomeFeedMode.HIDDEN),
        coverBounds = mapOf(CoverKind.FEED to rect),
        visibleCovers = mapOf(CoverKind.FEED to rect),
        audioFocusHeld = true,
        targets = ActionTarget.entries.toSet(),
        interaction = interaction,
    )

    private fun flow(
        step: FeedNavigationStep,
        destination: FeedDestination = FeedDestination.FOLLOWING,
    ) = FeedNavigationSnapshot(destination, step)

    @Test fun followingAndFavoritesStartWithLogoTap() {
        for (interaction in listOf(InstagramInteraction.OPEN_FOLLOWING, InstagramInteraction.OPEN_FAVORITES)) {
            assertEquals(listOf(Action.Tap(ActionTarget.INSTAGRAM_LOGO)), policy.decide(home(interaction)).actions)
        }
    }

    @Test fun navigationTapsHomeFirstIfNeededThenLogoOnFreshSnapshot() {
        val snapshot = InstagramSnapshot(InstagramScreen.PROFILE, targets = ActionTarget.entries.toSet(),
            feedNavigation = flow(FeedNavigationStep.HOME_TAB))
        assertEquals(listOf(Action.Tap(ActionTarget.HOME_TAB)), policy.decide(snapshot).actions)
        assertEquals(listOf(Action.Tap(ActionTarget.INSTAGRAM_LOGO)), policy.decide(
            home().copy(feedNavigation = flow(FeedNavigationStep.HOME_TAB)),
        ).actions)
    }

    @Test fun followingSelectsFirstMenuEntryAndFavoritesSecond() {
        for ((destination, target) in listOf(
            FeedDestination.FOLLOWING to ActionTarget.FOLLOWING_MENU_ENTRY,
            FeedDestination.FAVORITES to ActionTarget.FAVORITES_MENU_ENTRY,
        )) {
            val snapshot = InstagramSnapshot(InstagramScreen.OVERLAY_UI, sheetOrMenuOpen = true,
                targets = setOf(target), feedNavigation = flow(FeedNavigationStep.MENU_ENTRY, destination)
                    .copy(menuEntryCount = 2))
            assertEquals(listOf(Action.Tap(target)), policy.decide(snapshot).actions)
        }
    }

    @Test fun menuWithAnythingOtherThanTwoEntriesIsNotTapped() {
        for (count in listOf(0, 1, 3)) {
            val snapshot = InstagramSnapshot(InstagramScreen.OVERLAY_UI, targets = ActionTarget.entries.toSet(),
                feedNavigation = flow(FeedNavigationStep.MENU_ENTRY).copy(menuEntryCount = count))
            val decision = policy.decide(snapshot)
            assertTrue(decision.actions.isEmpty())
            assertEquals(2_500L, decision.recheckAfterMs)
        }
    }

    @Test fun alreadyAttemptedTapIsNeverRepeated() {
        val snapshot = home().copy(feedNavigation = flow(FeedNavigationStep.LOGO_MENU).copy(
            attemptedTargets = setOf(ActionTarget.INSTAGRAM_LOGO), msSinceProgress = 500,
        ))
        assertEquals(PolicyDecision(recheckAfterMs = 2_000), policy.decide(snapshot))
    }

    @Test fun noProgressAt2499MsWaitsAndAt2500MsShowsFailureMessage() {
        val navigation = flow(FeedNavigationStep.DESTINATION_CONFIRMATION)
        val snapshot = InstagramSnapshot(InstagramScreen.OVERLAY_UI,
            feedNavigation = navigation.copy(msSinceProgress = 2_499))
        assertEquals(PolicyDecision(recheckAfterMs = 1), policy.decide(snapshot))
        assertEquals(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)), policy.decide(snapshot.copy(
            feedNavigation = navigation.copy(msSinceProgress = 2_500),
        )).actions)
    }

    @Test fun screenClassificationAloneDoesNotConfirmDestinationTitle() {
        val snapshot = InstagramSnapshot(InstagramScreen.FOLLOWING,
            feedNavigation = flow(FeedNavigationStep.DESTINATION_CONFIRMATION))
        assertEquals(2_500L, policy.decide(snapshot).recheckAfterMs)
        assertEquals(PolicyDecision(), policy.decide(snapshot.copy(feedNavigation = snapshot.feedNavigation!!.copy(
            destinationTitleMatches = true, msSinceProgress = 2_500,
        ))))
    }

    @Test fun completedOrStoppedWorkflowDoesNotTapOrRepeatTimeoutMessage() {
        for (step in listOf(FeedNavigationStep.COMPLETE, FeedNavigationStep.STOPPED)) {
            assertEquals(PolicyDecision(), policy.decide(home().copy(
                feedNavigation = flow(step).copy(msSinceProgress = 3_000),
            )))
        }
    }

    @Test fun missingLogoTargetWaitsWithoutTapOrBack() {
        val decision = policy.decide(home(InstagramInteraction.OPEN_FOLLOWING).copy(targets = emptySet()))
        assertTrue(decision.actions.isEmpty())
        assertEquals(2_500L, decision.recheckAfterMs)
    }

    @Test fun reportedFailedTapShowsFailureWithoutBackOnLaterSnapshot() {
        val snapshot = home().copy(feedNavigation = flow(FeedNavigationStep.LOGO_MENU),
            failedTap = ActionTarget.INSTAGRAM_LOGO)
        assertEquals(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)), policy.decide(snapshot).actions)
    }

    @Test fun searchFromFeedTapsTabThenBoxWithSuppliedProgress() {
        assertEquals(listOf(Action.Tap(ActionTarget.SEARCH_TAB)), policy.decide(
            home(InstagramInteraction.OPEN_SEARCH),
        ).actions)
        val snapshot = InstagramSnapshot(InstagramScreen.EXPLORE, targets = setOf(ActionTarget.SEARCH_BOX),
            feedNavigation = flow(FeedNavigationStep.SEARCH_BOX, FeedDestination.SEARCH))
        assertEquals(listOf(Action.Tap(ActionTarget.SEARCH_BOX)), policy.decide(snapshot).actions)
    }

    @Test fun searchFromExplorePanelTapsSearchBox() {
        val snapshot = InstagramSnapshot(InstagramScreen.EXPLORE,
            settings = InstagramSettings(hideExplore = true),
            coverBounds = mapOf(CoverKind.EXPLORE to rect),
            visibleCovers = mapOf(CoverKind.EXPLORE to rect),
            interaction = InstagramInteraction.OPEN_SEARCH, targets = setOf(ActionTarget.SEARCH_BOX))
        assertEquals(listOf(Action.Tap(ActionTarget.SEARCH_BOX)), policy.decide(snapshot).actions)
    }

    @Test fun caughtUpGoBackUpScrollsToResolvedTargetWithoutTapOrBack() {
        val snapshot = caughtUp(InstagramInteraction.GO_BACK_UP)
        assertEquals(listOf(Action.ScrollTo(ActionTarget.FEED_ABOVE_CAUGHT_UP)), policy.decide(snapshot).actions)
        assertTrue(policy.decide(snapshot.copy(targets = emptySet())).actions.isEmpty())
    }

    @Test fun caughtUpViewOlderPostsTapsInstagramLinkOnlyWhenFound() {
        val snapshot = caughtUp(InstagramInteraction.VIEW_OLDER_POSTS)
        assertEquals(listOf(Action.Tap(ActionTarget.OLDER_POSTS_LINK)), policy.decide(snapshot).actions)
        assertTrue(policy.decide(snapshot.copy(targets = emptySet())).actions.isEmpty())
    }

    @Test fun failedViewOlderPostsTapShowsFailureWithoutBack() {
        assertEquals(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)),
            policy.decide(caughtUp(InstagramInteraction.VIEW_OLDER_POSTS)
            .copy(failedTap = ActionTarget.OLDER_POSTS_LINK)).actions)
    }

    @Test fun everyPanelTapFailureReportsFailureWithoutBackOrRetry() {
        val steps = listOf(
            Triple(FeedDestination.FOLLOWING, FeedNavigationStep.HOME_TAB, ActionTarget.HOME_TAB),
            Triple(FeedDestination.FOLLOWING, FeedNavigationStep.LOGO_MENU, ActionTarget.INSTAGRAM_LOGO),
            Triple(FeedDestination.FOLLOWING, FeedNavigationStep.MENU_ENTRY, ActionTarget.FOLLOWING_MENU_ENTRY),
            Triple(FeedDestination.FAVORITES, FeedNavigationStep.MENU_ENTRY, ActionTarget.FAVORITES_MENU_ENTRY),
            Triple(FeedDestination.SEARCH, FeedNavigationStep.SEARCH_TAB, ActionTarget.SEARCH_TAB),
            Triple(FeedDestination.SEARCH, FeedNavigationStep.SEARCH_BOX, ActionTarget.SEARCH_BOX),
            Triple(FeedDestination.OLDER_POSTS, FeedNavigationStep.OLDER_POSTS_LINK, ActionTarget.OLDER_POSTS_LINK),
        )
        for ((destination, step, target) in steps) {
            val snapshot = InstagramSnapshot(
                screen = if (step == FeedNavigationStep.HOME_TAB) InstagramScreen.PROFILE
                    else InstagramScreen.HOME_FEED,
                feedNavigation = flow(step, destination).copy(menuEntryCount = 2),
                targets = ActionTarget.entries.toSet(), failedTap = target,
            )
            assertEquals(PolicyDecision(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED))),
                policy.decide(snapshot), "$destination $step")
        }
    }

    @Test fun goBackUpScrollFailureReportsFailureWithoutAnotherButtonPress() {
        val snapshot = caughtUp(InstagramInteraction.GO_BACK_UP).copy(interaction = null,
            feedNavigation = flow(FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP, FeedDestination.CAUGHT_UP_TOP)
                .copy(stepFailed = true))
        assertEquals(PolicyDecision(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED))),
            policy.decide(snapshot))
    }

    @Test fun everyPanelWorkflowTimesOutWithoutBackOrRetry() {
        for (navigation in panelWorkflows()) {
            val snapshot = InstagramSnapshot(InstagramScreen.HOME_FEED,
                feedNavigation = navigation.copy(msSinceProgress = 2_500),
                targets = ActionTarget.entries.toSet())
            assertEquals(PolicyDecision(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED))),
                policy.decide(snapshot), navigation.destination.toString())
        }
    }

    @Test fun stoppedPanelWorkflowDoesNothingAfterFailureEvenWithStaleButtonInteraction() {
        for (navigation in panelWorkflows()) {
            val snapshot = home(InstagramInteraction.OPEN_FAVORITES).copy(
                feedNavigation = navigation.copy(stepFailed = true))
            assertEquals(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)),
                policy.decide(snapshot).actions)
            assertEquals(PolicyDecision(), policy.decide(snapshot.copy(
                feedNavigation = snapshot.feedNavigation!!.copy(step = FeedNavigationStep.STOPPED),
            )))
        }
    }

    @Test fun goBackUpScrollIsNotRepeatedWhileWaitingForProgress() {
        val snapshot = caughtUp(InstagramInteraction.GO_BACK_UP).copy(
            feedNavigation = flow(FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP, FeedDestination.CAUGHT_UP_TOP)
                .copy(attemptedTargets = setOf(ActionTarget.FEED_ABOVE_CAUGHT_UP), msSinceProgress = 2_499))
        assertEquals(PolicyDecision(recheckAfterMs = 1), policy.decide(snapshot))
    }

    private fun panelWorkflows() = listOf(
        flow(FeedNavigationStep.LOGO_MENU, FeedDestination.FOLLOWING),
        flow(FeedNavigationStep.LOGO_MENU, FeedDestination.FAVORITES),
        flow(FeedNavigationStep.SEARCH_TAB, FeedDestination.SEARCH),
        flow(FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP, FeedDestination.CAUGHT_UP_TOP),
        flow(FeedNavigationStep.OLDER_POSTS_LINK, FeedDestination.OLDER_POSTS),
    )

    @Test fun panelInteractionsAreIgnoredWhenPanelIsNotApplicable() {
        val snapshot = InstagramSnapshot(InstagramScreen.PROFILE,
            interaction = InstagramInteraction.OPEN_FOLLOWING, targets = ActionTarget.entries.toSet())
        assertEquals(PolicyDecision(), policy.decide(snapshot))
    }

    @Test fun dmsOnlyIgnoresFeedNavigationWorkflow() {
        val snapshot = home().copy(settings = InstagramSettings(dmsOnly = true),
            feedNavigation = flow(FeedNavigationStep.LOGO_MENU))
        assertFalse(policy.decide(snapshot).actions.any { it is Action.Tap })
    }

    @Test fun unknownScreenCannotContinueNavigation() {
        assertEquals(PolicyDecision(), policy.decide(InstagramSnapshot(
            InstagramScreen.UNKNOWN, targets = ActionTarget.entries.toSet(),
            feedNavigation = flow(FeedNavigationStep.MENU_ENTRY).copy(menuEntryCount = 2),
        )))
    }

    private fun caughtUp(interaction: InstagramInteraction) = InstagramSnapshot(
        screen = InstagramScreen.HOME_FEED,
        caughtUpRowReached = true,
        coverBounds = mapOf(CoverKind.CAUGHT_UP to rect),
        visibleCovers = mapOf(CoverKind.CAUGHT_UP to rect),
        audioFocusHeld = true,
        targets = ActionTarget.entries.toSet(),
        interaction = interaction,
    )
}
