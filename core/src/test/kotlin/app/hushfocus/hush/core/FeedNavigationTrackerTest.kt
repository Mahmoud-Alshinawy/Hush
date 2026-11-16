package app.hushfocus.hush.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedNavigationTrackerTest {
    private val time = FakeTime()
    private val tracker = FeedNavigationTracker()
    private val policy = InstagramPolicy()

    private fun observation(screen: InstagramScreen = InstagramScreen.HOME_FEED) = InstagramObservation(
        time.nowMs, screen, targets = ActionTarget.entries.toSet(),
    )
    private fun decide(observation: InstagramObservation): PolicyDecision = policy.decide(InstagramSnapshot(
        screen = observation.screen, targets = observation.targets, sheetOrMenuOpen = observation.sheetOrMenuOpen,
        feedNavigation = tracker.snapshot(time.nowMs),
    ))
    private fun executed(action: Action, succeeded: Boolean = true) {
        tracker.recordExecution(ActionExecutionFeedback(time.nowMs, action, succeeded))
    }

    @Test fun fullFavoritesPathAdvancesOnlyOnObservedProgress() {
        val home = observation()
        tracker.start(InstagramInteraction.OPEN_FAVORITES, home, time.nowMs)
        tracker.observe(home)
        assertEquals(listOf(Action.Tap(ActionTarget.INSTAGRAM_LOGO)), decide(home).actions)
        executed(Action.Tap(ActionTarget.INSTAGRAM_LOGO))
        assertEquals(FeedNavigationStep.LOGO_MENU, tracker.snapshot(time.nowMs)!!.step)

        time.advanceBy(100)
        val menu = observation(InstagramScreen.OVERLAY_UI).copy(sheetOrMenuOpen = true, menuEntryCount = 2)
        tracker.observe(menu)
        assertEquals(FeedNavigationStep.MENU_ENTRY, tracker.snapshot(time.nowMs)!!.step)
        assertEquals(0L, tracker.snapshot(time.nowMs)!!.msSinceProgress)
        assertEquals(listOf(Action.Tap(ActionTarget.FAVORITES_MENU_ENTRY)), decide(menu).actions)
        executed(Action.Tap(ActionTarget.FAVORITES_MENU_ENTRY))

        time.advanceBy(100)
        val destination = observation(InstagramScreen.FAVORITES).copy(destinationTitleMatches = true)
        tracker.observe(destination)
        assertEquals(FeedNavigationStep.COMPLETE, tracker.snapshot(time.nowMs)!!.step)
        assertEquals(setOf(ActionTarget.INSTAGRAM_LOGO, ActionTarget.FAVORITES_MENU_ENTRY),
            tracker.snapshot(time.nowMs)!!.attemptedTargets)
        assertTrue(decide(destination).actions.isEmpty())
    }

    @Test fun homeTabIsReachedBeforeOpeningLogoMenu() {
        val profile = observation(InstagramScreen.PROFILE)
        tracker.start(InstagramInteraction.OPEN_FOLLOWING, profile, time.nowMs)
        assertEquals(listOf(Action.Tap(ActionTarget.HOME_TAB)), decide(profile).actions)
        executed(Action.Tap(ActionTarget.HOME_TAB))
        time.advanceBy(700)
        val home = observation()
        tracker.observe(home)
        assertEquals(FeedNavigationStep.LOGO_MENU, tracker.snapshot(time.nowMs)!!.step)
        assertEquals(0L, tracker.snapshot(time.nowMs)!!.msSinceProgress)
        assertEquals(listOf(Action.Tap(ActionTarget.INSTAGRAM_LOGO)), decide(home).actions)
    }

    @Test fun threeEntryMenuNeverTapsAndEventuallyStopsAfterFailureMessage() {
        tracker.start(InstagramInteraction.OPEN_FAVORITES, observation(), time.nowMs)
        executed(Action.Tap(ActionTarget.INSTAGRAM_LOGO))
        time.advanceBy(100)
        val menu = observation(InstagramScreen.OVERLAY_UI).copy(sheetOrMenuOpen = true, menuEntryCount = 3)
        tracker.observe(menu)
        assertTrue(decide(menu).actions.isEmpty())
        assertEquals(FeedNavigationStep.MENU_ENTRY, tracker.snapshot(time.nowMs)!!.step)
        time.advanceBy(2_400)
        val failed = decide(menu)
        assertEquals(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)), failed.actions)
        tracker.recordDecision(failed)
        assertEquals(FeedNavigationStep.STOPPED, tracker.snapshot(time.nowMs)!!.step)
        tracker.observe(menu.copy(atMs = time.nowMs, menuEntryCount = 2))
        assertEquals(PolicyDecision(), decide(menu))
    }

    @Test fun invalidMenuDoesNotResetTimeoutButBecomingValidDoes() {
        tracker.start(InstagramInteraction.OPEN_FAVORITES, observation(), time.nowMs)
        executed(Action.Tap(ActionTarget.INSTAGRAM_LOGO))
        time.advanceBy(100)
        tracker.observe(observation(InstagramScreen.OVERLAY_UI).copy(sheetOrMenuOpen = true, menuEntryCount = 3))
        assertEquals(100L, tracker.snapshot(time.nowMs)!!.msSinceProgress)
        time.advanceBy(400)
        tracker.observe(observation(InstagramScreen.OVERLAY_UI).copy(sheetOrMenuOpen = true, menuEntryCount = 2))
        assertEquals(0L, tracker.snapshot(time.nowMs)!!.msSinceProgress)
    }

    @Test fun noRepeatedTapWhileWaitingForProgress() {
        val home = observation()
        tracker.start(InstagramInteraction.OPEN_FOLLOWING, home, time.nowMs)
        executed(Action.Tap(ActionTarget.INSTAGRAM_LOGO))
        time.advanceBy(500)
        tracker.observe(home.copy(atMs = time.nowMs))
        assertEquals(PolicyDecision(recheckAfterMs = 2_000), decide(home))
    }

    @Test fun failedAttemptBecomesStepFailureThenStoppedWithoutBack() {
        val home = observation()
        tracker.start(InstagramInteraction.OPEN_FAVORITES, home, time.nowMs)
        executed(Action.Tap(ActionTarget.INSTAGRAM_LOGO), succeeded = false)
        assertTrue(tracker.snapshot(time.nowMs)!!.stepFailed)
        val decision = decide(home)
        assertEquals(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)), decision.actions)
        tracker.recordDecision(decision)
        assertEquals(PolicyDecision(), decide(home))
    }

    @Test fun explicitNewInteractionResetsStoppedWorkflowAndAttempts() {
        val home = observation()
        tracker.start(InstagramInteraction.OPEN_FAVORITES, home, time.nowMs)
        executed(Action.Tap(ActionTarget.INSTAGRAM_LOGO), succeeded = false)
        tracker.recordDecision(decide(home))
        time.advanceBy(1_000)
        tracker.start(InstagramInteraction.OPEN_FAVORITES, home.copy(atMs = time.nowMs), time.nowMs)
        assertFalse(tracker.snapshot(time.nowMs)!!.stepFailed)
        assertTrue(tracker.snapshot(time.nowMs)!!.attemptedTargets.isEmpty())
        assertEquals(listOf(Action.Tap(ActionTarget.INSTAGRAM_LOGO)), decide(home).actions)
    }

    @Test fun wrongDestinationTitleDoesNotCompleteWorkflow() {
        tracker.start(InstagramInteraction.OPEN_FAVORITES, observation(), time.nowMs)
        tracker.observe(observation(InstagramScreen.OVERLAY_UI).copy(sheetOrMenuOpen = true, menuEntryCount = 2))
        executed(Action.Tap(ActionTarget.FAVORITES_MENU_ENTRY))
        time.advanceBy(100)
        tracker.observe(observation(InstagramScreen.FAVORITES))
        assertEquals(FeedNavigationStep.DESTINATION_CONFIRMATION, tracker.snapshot(time.nowMs)!!.step)
        time.advanceBy(100)
        tracker.observe(observation(InstagramScreen.FAVORITES).copy(destinationTitleMatches = true))
        assertEquals(FeedNavigationStep.COMPLETE, tracker.snapshot(time.nowMs)!!.step)
    }

    @Test fun searchRequiresTabThenFocusedBoxNotMerelyAvailableTarget() {
        tracker.start(InstagramInteraction.OPEN_SEARCH, observation(), time.nowMs)
        executed(Action.Tap(ActionTarget.SEARCH_TAB))
        time.advanceBy(100)
        tracker.observe(observation(InstagramScreen.EXPLORE))
        assertEquals(FeedNavigationStep.SEARCH_BOX, tracker.snapshot(time.nowMs)!!.step)
        executed(Action.Tap(ActionTarget.SEARCH_BOX))
        tracker.observe(observation(InstagramScreen.EXPLORE))
        assertEquals(FeedNavigationStep.SEARCH_BOX, tracker.snapshot(time.nowMs)!!.step)
        tracker.observe(observation(InstagramScreen.EXPLORE).copy(searchBoxFocused = true))
        assertEquals(FeedNavigationStep.COMPLETE, tracker.snapshot(time.nowMs)!!.step)
    }

    @Test fun exploreSearchStartsAtBox() {
        tracker.start(InstagramInteraction.OPEN_SEARCH, observation(InstagramScreen.EXPLORE), time.nowMs)
        assertEquals(FeedNavigationStep.SEARCH_BOX, tracker.snapshot(time.nowMs)!!.step)
    }

    @Test fun goBackUpNeedsAttemptAndObservedScrollCompletion() {
        val home = observation().copy(caughtUpRowReached = true)
        tracker.start(InstagramInteraction.GO_BACK_UP, home, time.nowMs)
        tracker.observe(home)
        assertEquals(FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP, tracker.snapshot(time.nowMs)!!.step)
        executed(Action.ScrollTo(ActionTarget.FEED_ABOVE_CAUGHT_UP))
        tracker.observe(home.copy(scrollCompleted = true))
        assertEquals(FeedNavigationStep.COMPLETE, tracker.snapshot(time.nowMs)!!.step)
    }

    @Test fun scrollingAboveRowAlsoCompletesGoBackUp() {
        tracker.start(InstagramInteraction.GO_BACK_UP, observation().copy(caughtUpRowReached = true), time.nowMs)
        executed(Action.ScrollTo(ActionTarget.FEED_ABOVE_CAUGHT_UP))
        tracker.observe(observation().copy(caughtUpRowReached = false))
        assertEquals(FeedNavigationStep.COMPLETE, tracker.snapshot(time.nowMs)!!.step)
    }

    @Test fun failedScrollUsesSameFailureAndStopPathAsFailedTap() {
        val home = observation().copy(caughtUpRowReached = true)
        tracker.start(InstagramInteraction.GO_BACK_UP, home, time.nowMs)
        executed(Action.ScrollTo(ActionTarget.FEED_ABOVE_CAUGHT_UP), succeeded = false)
        val decision = decide(home)
        assertEquals(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)), decision.actions)
        tracker.recordDecision(decision)
        assertEquals(FeedNavigationStep.STOPPED, tracker.snapshot(time.nowMs)!!.step)
    }

    @Test fun olderPostsNeedsObservedNavigationAfterTap() {
        val home = observation().copy(caughtUpRowReached = true)
        tracker.start(InstagramInteraction.VIEW_OLDER_POSTS, home, time.nowMs)
        executed(Action.Tap(ActionTarget.OLDER_POSTS_LINK))
        tracker.observe(home)
        assertEquals(FeedNavigationStep.OLDER_POSTS_LINK, tracker.snapshot(time.nowMs)!!.step)
        tracker.observe(home.copy(olderPostsOpened = true))
        assertEquals(FeedNavigationStep.COMPLETE, tracker.snapshot(time.nowMs)!!.step)
    }

    @Test fun unrelatedExecutionDoesNotConsumeWorkflowAttempt() {
        tracker.start(InstagramInteraction.OPEN_FAVORITES, observation(), time.nowMs)
        executed(Action.Tap(ActionTarget.DMS_TAB), succeeded = false)
        assertTrue(tracker.snapshot(time.nowMs)!!.attemptedTargets.isEmpty())
        assertFalse(tracker.snapshot(time.nowMs)!!.stepFailed)
    }

    @Test fun unknownCannotAdvanceWorkflow() {
        tracker.start(InstagramInteraction.OPEN_FAVORITES, observation(), time.nowMs)
        tracker.observe(observation(InstagramScreen.UNKNOWN).copy(sheetOrMenuOpen = true, menuEntryCount = 2))
        assertEquals(FeedNavigationStep.LOGO_MENU, tracker.snapshot(time.nowMs)!!.step)
    }
}
