package app.hushfocus.hush.core

class FeedNavigationTracker {
    private var navigation: FeedNavigationSnapshot? = null
    private var progressAtMs = 0L

    fun start(interaction: InstagramInteraction, observation: InstagramObservation, nowMs: Long) {
        val destination = when (interaction) {
            InstagramInteraction.OPEN_FOLLOWING -> FeedDestination.FOLLOWING
            InstagramInteraction.OPEN_FAVORITES -> FeedDestination.FAVORITES
            InstagramInteraction.OPEN_SEARCH -> FeedDestination.SEARCH
            InstagramInteraction.GO_BACK_UP -> FeedDestination.CAUGHT_UP_TOP
            InstagramInteraction.VIEW_OLDER_POSTS -> FeedDestination.OLDER_POSTS
            else -> return
        }
        val step = when (destination) {
            FeedDestination.FOLLOWING, FeedDestination.FAVORITES ->
                if (observation.screen == InstagramScreen.HOME_FEED) FeedNavigationStep.LOGO_MENU
                else FeedNavigationStep.HOME_TAB
            FeedDestination.SEARCH -> if (observation.screen == InstagramScreen.EXPLORE) {
                FeedNavigationStep.SEARCH_BOX
            } else FeedNavigationStep.SEARCH_TAB
            FeedDestination.CAUGHT_UP_TOP -> FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP
            FeedDestination.OLDER_POSTS -> FeedNavigationStep.OLDER_POSTS_LINK
        }
        navigation = FeedNavigationSnapshot(destination, step)
        progressAtMs = nowMs
    }

    fun observe(observation: InstagramObservation) {
        val current = navigation ?: return
        if (current.isTerminal()) return
        navigation = current.copy(menuEntryCount = observation.menuEntryCount,
            destinationTitleMatches = observation.destinationTitleMatches)
        if (current.stepFailed || observation.screen == InstagramScreen.UNKNOWN) return

        val titleConfirmed = observation.destinationTitleMatches && when (current.destination) {
            FeedDestination.FOLLOWING -> observation.screen == InstagramScreen.FOLLOWING
            FeedDestination.FAVORITES -> observation.screen == InstagramScreen.FAVORITES
            else -> false
        }
        var resetProgressTimer = true
        val next = when (current.step) {
            FeedNavigationStep.HOME_TAB ->
                if (observation.screen == InstagramScreen.HOME_FEED) FeedNavigationStep.LOGO_MENU else null
            FeedNavigationStep.LOGO_MENU ->
                if (observation.sheetOrMenuOpen || observation.screen == InstagramScreen.OVERLAY_UI) {
                    // Waiting at MENU_ENTRY prevents another Home/logo tap on an invalid menu.
                    resetProgressTimer = observation.menuEntryCount == 2
                    FeedNavigationStep.MENU_ENTRY
                } else null
            FeedNavigationStep.MENU_ENTRY -> when {
                titleConfirmed -> FeedNavigationStep.COMPLETE
                current.menuEntryCount != 2 && observation.menuEntryCount == 2 && observation.sheetOrMenuOpen ->
                    FeedNavigationStep.MENU_ENTRY
                menuTarget(current.destination) in current.attemptedTargets && !observation.sheetOrMenuOpen ->
                    FeedNavigationStep.DESTINATION_CONFIRMATION
                else -> null
            }
            FeedNavigationStep.DESTINATION_CONFIRMATION ->
                if (titleConfirmed) FeedNavigationStep.COMPLETE else null
            FeedNavigationStep.SEARCH_TAB -> when {
                observation.searchBoxFocused -> FeedNavigationStep.COMPLETE
                observation.screen == InstagramScreen.EXPLORE ||
                    observation.screen == InstagramScreen.SEARCH_RESULTS -> FeedNavigationStep.SEARCH_BOX
                else -> null
            }
            FeedNavigationStep.SEARCH_BOX ->
                if (observation.searchBoxFocused) FeedNavigationStep.COMPLETE else null
            FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP ->
                if (ActionTarget.FEED_ABOVE_CAUGHT_UP in current.attemptedTargets &&
                    (observation.scrollCompleted || !observation.caughtUpRowReached)
                ) FeedNavigationStep.COMPLETE else null
            FeedNavigationStep.OLDER_POSTS_LINK ->
                if (ActionTarget.OLDER_POSTS_LINK in current.attemptedTargets && observation.olderPostsOpened) {
                    FeedNavigationStep.COMPLETE
                } else null
            else -> null
        }
        if (next != null) {
            navigation = navigation?.copy(step = next)
            if (resetProgressTimer) progressAtMs = observation.atMs
        }
    }

    fun recordExecution(feedback: ActionExecutionFeedback) {
        val current = navigation ?: return
        if (current.isTerminal()) return
        val target = when (val action = feedback.action) {
            is Action.Tap -> action.target
            is Action.ScrollTo -> action.target
            else -> return
        }
        val expected = when (current.step) {
            FeedNavigationStep.HOME_TAB, FeedNavigationStep.LOGO_MENU ->
                setOf(ActionTarget.HOME_TAB, ActionTarget.INSTAGRAM_LOGO)
            FeedNavigationStep.MENU_ENTRY -> setOfNotNull(menuTarget(current.destination))
            FeedNavigationStep.SEARCH_TAB -> setOf(ActionTarget.SEARCH_TAB)
            FeedNavigationStep.SEARCH_BOX -> setOf(ActionTarget.SEARCH_BOX)
            FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP -> setOf(ActionTarget.FEED_ABOVE_CAUGHT_UP)
            FeedNavigationStep.OLDER_POSTS_LINK -> setOf(ActionTarget.OLDER_POSTS_LINK)
            else -> emptySet()
        }
        if (target in expected) navigation = current.copy(
            attemptedTargets = current.attemptedTargets + target,
            stepFailed = current.stepFailed || !feedback.succeeded,
        )
    }

    fun recordDecision(decision: PolicyDecision) {
        if (decision.actions.any { it == Action.ShowMessage(HushMessage.NAVIGATION_FAILED) }) stop()
    }

    fun stop() { navigation = navigation?.copy(step = FeedNavigationStep.STOPPED) }

    fun snapshot(nowMs: Long): FeedNavigationSnapshot? = navigation?.copy(
        msSinceProgress = (nowMs - progressAtMs).coerceAtLeast(0),
    )

    private fun FeedNavigationSnapshot.isTerminal(): Boolean =
        step == FeedNavigationStep.STOPPED || step == FeedNavigationStep.COMPLETE

    private fun menuTarget(destination: FeedDestination): ActionTarget? = when (destination) {
        FeedDestination.FOLLOWING -> ActionTarget.FOLLOWING_MENU_ENTRY
        FeedDestination.FAVORITES -> ActionTarget.FAVORITES_MENU_ENTRY
        else -> null
    }
}
