package app.hushfocus.hush.core

class InstagramFeedNavigationPolicy {
    fun decide(snapshot: InstagramSnapshot, covers: Set<CoverKind>): PolicyDecision {
        if (snapshot.screen == InstagramScreen.UNKNOWN || snapshot.settings.dmsOnly ||
            snapshot.reelPlayerVisible
        ) return PolicyDecision()

        val navigation = snapshot.feedNavigation ?: startNavigation(snapshot, covers)
        return if (navigation != null) navigationDecision(snapshot, navigation) else PolicyDecision()
    }

    private fun startNavigation(
        snapshot: InstagramSnapshot,
        covers: Set<CoverKind>,
    ): FeedNavigationSnapshot? = when {
        CoverKind.FEED in covers && snapshot.interaction == InstagramInteraction.OPEN_FOLLOWING ->
            FeedNavigationSnapshot(FeedDestination.FOLLOWING, FeedNavigationStep.LOGO_MENU)
        CoverKind.FEED in covers && snapshot.interaction == InstagramInteraction.OPEN_FAVORITES ->
            FeedNavigationSnapshot(FeedDestination.FAVORITES, FeedNavigationStep.LOGO_MENU)
        CoverKind.CAUGHT_UP in covers && snapshot.interaction == InstagramInteraction.GO_BACK_UP ->
            FeedNavigationSnapshot(FeedDestination.CAUGHT_UP_TOP, FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP)
        CoverKind.CAUGHT_UP in covers && snapshot.interaction == InstagramInteraction.VIEW_OLDER_POSTS ->
            FeedNavigationSnapshot(FeedDestination.OLDER_POSTS, FeedNavigationStep.OLDER_POSTS_LINK)
        (CoverKind.FEED in covers || CoverKind.EXPLORE in covers) &&
            snapshot.interaction == InstagramInteraction.OPEN_SEARCH -> FeedNavigationSnapshot(
                FeedDestination.SEARCH,
                if (snapshot.screen == InstagramScreen.EXPLORE) FeedNavigationStep.SEARCH_BOX
                else FeedNavigationStep.SEARCH_TAB,
            )
        else -> null
    }

    private fun navigationDecision(
        snapshot: InstagramSnapshot,
        navigation: FeedNavigationSnapshot,
    ): PolicyDecision {
        if (navigation.step == FeedNavigationStep.COMPLETE || navigation.step == FeedNavigationStep.STOPPED) {
            return PolicyDecision()
        }
        if (navigation.step == FeedNavigationStep.DESTINATION_CONFIRMATION &&
            navigation.destinationTitleMatches
        ) return PolicyDecision()
        if (navigation.stepFailed || navigation.msSinceProgress >= 2_500) return navigationFailed()

        val target = when (navigation.step) {
            FeedNavigationStep.HOME_TAB -> if (snapshot.screen == InstagramScreen.HOME_FEED) {
                ActionTarget.INSTAGRAM_LOGO
            } else ActionTarget.HOME_TAB
            FeedNavigationStep.LOGO_MENU -> if (snapshot.screen == InstagramScreen.HOME_FEED) {
                ActionTarget.INSTAGRAM_LOGO
            } else ActionTarget.HOME_TAB
            FeedNavigationStep.MENU_ENTRY -> if (navigation.menuEntryCount == 2) {
                when (navigation.destination) {
                    FeedDestination.FOLLOWING -> ActionTarget.FOLLOWING_MENU_ENTRY
                    FeedDestination.FAVORITES -> ActionTarget.FAVORITES_MENU_ENTRY
                    else -> null
                }
            } else null
            FeedNavigationStep.SEARCH_TAB -> ActionTarget.SEARCH_TAB
            FeedNavigationStep.SEARCH_BOX -> ActionTarget.SEARCH_BOX
            FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP -> ActionTarget.FEED_ABOVE_CAUGHT_UP
            FeedNavigationStep.OLDER_POSTS_LINK -> ActionTarget.OLDER_POSTS_LINK
            else -> null
        }
        val delay = 2_500 - navigation.msSinceProgress
        if (target == null) return PolicyDecision(recheckAfterMs = delay)
        if (snapshot.failedTap == target) return navigationFailed()
        if (target in navigation.attemptedTargets) return PolicyDecision(recheckAfterMs = delay)
        if (target !in snapshot.targets) return PolicyDecision(recheckAfterMs = delay)
        val action = if (navigation.step == FeedNavigationStep.SCROLL_ABOVE_CAUGHT_UP) {
            Action.ScrollTo(target)
        } else Action.Tap(target)
        return PolicyDecision(listOf(action), delay)
    }

    private fun navigationFailed(): PolicyDecision =
        PolicyDecision(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)))
}
