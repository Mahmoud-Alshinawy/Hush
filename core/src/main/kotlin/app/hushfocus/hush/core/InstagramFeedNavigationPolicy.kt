package app.hushfocus.hush.core

class InstagramFeedNavigationPolicy {
    fun decide(snapshot: InstagramSnapshot, covers: Set<CoverKind>): PolicyDecision {
        if (snapshot.screen == InstagramScreen.UNKNOWN || snapshot.settings.dmsOnly ||
            snapshot.reelPlayerVisible
        ) return PolicyDecision()

        val navigation = snapshot.feedNavigation ?: startNavigation(snapshot, covers)
        if (navigation != null) return navigationDecision(snapshot, navigation)

        return when {
            CoverKind.CAUGHT_UP !in covers -> PolicyDecision()
            snapshot.interaction == InstagramInteraction.GO_BACK_UP -> {
                if (ActionTarget.FEED_ABOVE_CAUGHT_UP in snapshot.targets) {
                    PolicyDecision(listOf(Action.ScrollTo(ActionTarget.FEED_ABOVE_CAUGHT_UP)))
                } else PolicyDecision()
            }
            snapshot.interaction == InstagramInteraction.VIEW_OLDER_POSTS -> {
                if (snapshot.failedTap == ActionTarget.OLDER_POSTS_LINK) PolicyDecision(listOf(Action.Back))
                else tapIfFound(snapshot, ActionTarget.OLDER_POSTS_LINK)
            }
            else -> PolicyDecision()
        }
    }

    private fun startNavigation(
        snapshot: InstagramSnapshot,
        covers: Set<CoverKind>,
    ): FeedNavigationSnapshot? = when {
        CoverKind.FEED in covers && snapshot.interaction == InstagramInteraction.OPEN_FOLLOWING ->
            FeedNavigationSnapshot(FeedDestination.FOLLOWING, FeedNavigationStep.LOGO_MENU)
        CoverKind.FEED in covers && snapshot.interaction == InstagramInteraction.OPEN_FAVORITES ->
            FeedNavigationSnapshot(FeedDestination.FAVORITES, FeedNavigationStep.LOGO_MENU)
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
        if (navigation.msSinceProgress >= 2_500) {
            return PolicyDecision(listOf(Action.ShowMessage(HushMessage.NAVIGATION_FAILED)))
        }

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
                    FeedDestination.SEARCH -> null
                }
            } else null
            FeedNavigationStep.SEARCH_TAB -> ActionTarget.SEARCH_TAB
            FeedNavigationStep.SEARCH_BOX -> ActionTarget.SEARCH_BOX
            else -> null
        }
        val delay = 2_500 - navigation.msSinceProgress
        if (target == null) return PolicyDecision(recheckAfterMs = delay)
        if (snapshot.failedTap == target) return PolicyDecision(listOf(Action.Back))
        if (target in navigation.attemptedTargets) return PolicyDecision(recheckAfterMs = delay)
        return tapIfFound(snapshot, target).copy(recheckAfterMs = delay)
    }

    private fun tapIfFound(snapshot: InstagramSnapshot, target: ActionTarget): PolicyDecision =
        if (target in snapshot.targets) PolicyDecision(listOf(Action.Tap(target))) else PolicyDecision()
}
