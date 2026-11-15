package app.hushfocus.hush.core

class InstagramPolicy(
    private val feedNavigationPolicy: InstagramFeedNavigationPolicy = InstagramFeedNavigationPolicy(),
) {
    fun decide(snapshot: InstagramSnapshot): PolicyDecision {
        if (snapshot.screen == InstagramScreen.UNKNOWN) {
            return PolicyDecision(coverActions(snapshot, emptyMap()))
        }

        val desiredCovers = desiredCovers(snapshot)
        val actions = coverActions(snapshot, desiredCovers).toMutableList()

        // A sheet suspends reel decisions, but must not bypass cover removal/audio cleanup.
        if (snapshot.reelPlayerVisible && snapshot.sheetOrMenuOpen) {
            return PolicyDecision(actions)
        }

        val contentDecision = when {
            snapshot.reelPlayerVisible -> playerDecision(snapshot)
            snapshot.settings.dmsOnly -> PolicyDecision(dmRedirect(snapshot))
            snapshot.screen == InstagramScreen.REELS_TAB -> {
                if (snapshot.allowance.hasTimeRemaining) PolicyDecision(listOf(Action.Allow))
                else PolicyDecision(listOf(redirectFromReelsTab(snapshot)))
            }
            snapshot.interaction == InstagramInteraction.CLOSE_STORY &&
                CoverKind.STORY_VIEWER in desiredCovers -> PolicyDecision(listOf(Action.Back))
            else -> feedNavigationPolicy.decide(snapshot, desiredCovers.keys)
        }
        actions += contentDecision.actions

        if (snapshot.interaction == InstagramInteraction.REELS_COVER_TAPPED &&
            CoverKind.REELS_TAB in desiredCovers
        ) {
            actions += Action.ShowMessage(HushMessage.REELS_BLOCKED)
        }
        if (snapshot.allowance.justExhausted && !snapshot.allowance.hasTimeRemaining) {
            actions += Action.ShowMessage(HushMessage.ALLOWANCE_USED_UP)
        }
        return PolicyDecision(actions, contentDecision.recheckAfterMs)
    }

    private fun playerDecision(snapshot: InstagramSnapshot): PolicyDecision =
        when {
            snapshot.allowance.hasTimeRemaining -> PolicyDecision(listOf(Action.Allow))
            else -> restrictedPlayerDecision(snapshot)
        }

    private fun restrictedPlayerDecision(snapshot: InstagramSnapshot): PolicyDecision {
        val origin = admittedOrigin(snapshot.reel)
        if (snapshot.settings.dmsOnly && origin != ReelOrigin.DM && origin != ReelOrigin.UNRESOLVED) {
            return PolicyDecision(listOf(Action.Back))
        }
        return when (origin) {
            ReelOrigin.DM -> dmReelDecision(snapshot)
            ReelOrigin.PROFILE, ReelOrigin.LINK -> oneReelDecision(snapshot, loadingGrace = true)
            ReelOrigin.STORY -> {
                if (snapshot.settings.hideStories || snapshot.reelsTabSelected) {
                    PolicyDecision(listOf(Action.Back))
                } else oneReelDecision(snapshot, loadingGrace = true)
            }
            ReelOrigin.HOME_FEED, ReelOrigin.EXPLORE,
            ReelOrigin.SEARCH_RESULTS, ReelOrigin.FAVORITES -> PolicyDecision(listOf(Action.Back))
            ReelOrigin.REELS_TAB -> PolicyDecision(listOf(redirectFromReelsTab(snapshot)))
            ReelOrigin.UNRESOLVED -> when {
                snapshot.replyBoxVisible -> dmReelDecision(snapshot)
                snapshot.replyBoxMissingMs >= 600 -> PolicyDecision(listOf(Action.Back))
                else -> PolicyDecision(listOf(Action.Allow), 600 - snapshot.replyBoxMissingMs)
            }
        }
    }

    private fun dmReelDecision(snapshot: InstagramSnapshot): PolicyDecision =
        if (!snapshot.settings.allowReelsSentInDms) PolicyDecision(listOf(Action.Back))
        else oneReelDecision(snapshot, loadingGrace = false)

    private fun oneReelDecision(snapshot: InstagramSnapshot, loadingGrace: Boolean): PolicyDecision =
        PolicyDecision(listOf(if (hasPagerSwipe(snapshot.reel, loadingGrace)) Action.Back else Action.Allow))

    private fun admittedOrigin(session: ReelSessionSnapshot): ReelOrigin {
        if (session.origin !in setOf(ReelOrigin.PROFILE, ReelOrigin.STORY, ReelOrigin.LINK)) {
            return session.origin
        }
        if (session.origin == ReelOrigin.LINK && !session.usageAccessGranted) return ReelOrigin.UNRESOLVED
        val ageAtOpen = session.originAgeAtOpenMs ?: return ReelOrigin.UNRESOLVED
        val openingDelay = session.msFromOriginToPlayer ?: return ReelOrigin.UNRESOLVED
        return if (ageAtOpen <= 5_000 && openingDelay <= 1_500) session.origin else ReelOrigin.UNRESOLVED
    }

    private fun hasPagerSwipe(session: ReelSessionSnapshot, loadingGrace: Boolean): Boolean {
        val changes = session.changes.filter { it.source == SwipeSource.REEL_PAGER }
        if (!loadingGrace) return changes.isNotEmpty()
        if (changes.any {
                it.change == ReelChange.VERTICAL_SCROLL_THRESHOLD ||
                    it.change == ReelChange.REELS_TAB_SELECTED || it.msSincePlayerOpened >= 1_500
            }
        ) return true
        return changes.count { it.change == ReelChange.PAGE_INDEX } > 1 ||
            changes.count { it.change == ReelChange.AUTHOR } > 1
    }

    private fun redirectFromReelsTab(snapshot: InstagramSnapshot): Action =
        if (ActionTarget.DMS_TAB in snapshot.targets && snapshot.failedTap != ActionTarget.DMS_TAB) {
            Action.Tap(ActionTarget.DMS_TAB)
        } else Action.Back

    private fun dmRedirect(snapshot: InstagramSnapshot): List<Action> {
        if (!snapshot.tabBarVisible || snapshot.screen == InstagramScreen.DM_INBOX ||
            snapshot.screen == InstagramScreen.DM_CHAT || ActionTarget.DMS_TAB !in snapshot.targets ||
            snapshot.failedTap == ActionTarget.DMS_TAB
        ) return emptyList()
        return listOf(Action.Tap(ActionTarget.DMS_TAB))
    }

    private fun desiredCovers(snapshot: InstagramSnapshot): Map<CoverKind, CoverRect> {
        if (snapshot.settings.dmsOnly) return emptyMap()
        val kinds = mutableSetOf<CoverKind>()
        if (snapshot.screen == InstagramScreen.HOME_FEED) {
            when (snapshot.settings.homeFeedMode) {
                HomeFeedMode.HIDDEN -> kinds += CoverKind.FEED
                HomeFeedMode.NORMAL -> if (snapshot.settings.stopAtCaughtUp && snapshot.caughtUpRowReached) {
                    kinds += CoverKind.CAUGHT_UP
                }
            }
            if (snapshot.settings.hideStories) kinds += CoverKind.STORIES_ROW
        }
        if (snapshot.screen == InstagramScreen.EXPLORE && snapshot.settings.hideExplore) {
            kinds += CoverKind.EXPLORE
        }
        if (snapshot.storyViewerVisible && snapshot.settings.hideStories) kinds += CoverKind.STORY_VIEWER

        if (!snapshot.allowance.hasTimeRemaining && !snapshot.sheetOrMenuOpen &&
            !snapshot.backArrowVisible && !snapshot.dmComposerVisible && !snapshot.storyViewerVisible &&
            !snapshot.reelPlayerVisible && !snapshot.replyBoxVisible
        ) kinds += CoverKind.REELS_TAB

        return snapshot.coverBounds.filterKeys { it in kinds }
    }

    private fun coverActions(
        snapshot: InstagramSnapshot,
        desired: Map<CoverKind, CoverRect>,
    ): List<Action> = buildList {
        for (kind in CoverKind.entries) {
            val rect = desired[kind]
            if (rect == null && kind in snapshot.visibleCovers) add(Action.HideCover(kind))
            else if (rect != null && snapshot.visibleCovers[kind] != rect) add(Action.ShowCover(kind, rect))
        }
        val needsAudioFocus = desired.keys.any {
            it == CoverKind.FEED || it == CoverKind.CAUGHT_UP || it == CoverKind.STORY_VIEWER
        }
        if (needsAudioFocus && !snapshot.audioFocusHeld) add(Action.RequestAudioFocus)
        if (!needsAudioFocus && snapshot.audioFocusHeld) add(Action.ReleaseAudioFocus)
    }
}
