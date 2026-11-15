package app.hushfocus.hush.core

data class PolicySnapshot(
    val app: AppId = AppId.INSTAGRAM,
    val settings: GlobalSettings = GlobalSettings(),
    val controls: GlobalControlState = GlobalControlState(),
    val instagram: InstagramSnapshot = InstagramSnapshot(),
    val msSinceLastNavigationAction: Long? = null,
) {
    init { require(msSinceLastNavigationAction == null || msSinceLastNavigationAction >= 0) }
}

/** Each flag already accounts for the foreground app, enabled settings and exemptions. */
data class GlobalControlState(
    val sleepBlocksApp: Boolean = false,
    val downtimeBlocksApp: Boolean = false,
    val dailyAppLimitReached: Boolean = false,
    val pauseActive: Boolean = false,
)

data class AllowanceState(
    val remainingMs: Long = 0,
    val justExhausted: Boolean = false,
) {
    init { require(remainingMs >= 0) }
    val hasTimeRemaining: Boolean get() = remainingMs > 0
}

/**
 * The tracker latches origin during opening (including late reply-box resolution).
 * It never reclassifies an established origin when the reply box disappears.
 * Opening evidence is frozen, not the age of that evidence at the current check.
 * After 1.5 s without the player the tracker supplies a new session and clears changes.
 */
data class ReelSessionSnapshot(
    val origin: ReelOrigin = ReelOrigin.UNRESOLVED,
    val originAgeAtOpenMs: Long? = null,
    val msFromOriginToPlayer: Long? = null,
    val usageAccessGranted: Boolean = false,
    val changes: List<ReelSwipeEvidence> = emptyList(),
) {
    init {
        require(originAgeAtOpenMs == null || originAgeAtOpenMs >= 0)
        require(msFromOriginToPlayer == null || msFromOriginToPlayer >= 0)
    }
}

data class InstagramSnapshot(
    val screen: InstagramScreen = InstagramScreen.UNKNOWN,
    val settings: InstagramSettings = InstagramSettings(),
    val reel: ReelSessionSnapshot = ReelSessionSnapshot(),
    val reelPlayerVisible: Boolean = screen == InstagramScreen.REEL_PLAYER,
    val storyViewerVisible: Boolean = screen == InstagramScreen.STORY_VIEWER,
    val reelsTabSelected: Boolean = false,
    val replyBoxVisible: Boolean = false,
    /** Opening-only unresolved-origin timer, supplied by the tracker. */
    val replyBoxMissingMs: Long = 0,
    val sheetOrMenuOpen: Boolean = false,
    val backArrowVisible: Boolean = false,
    val dmComposerVisible: Boolean = false,
    val tabBarVisible: Boolean = false,
    val allowance: AllowanceState = AllowanceState(),
    val targets: Set<ActionTarget> = emptySet(),
    /** Only validated, currently available rectangles; no guessed geometry. */
    val coverBounds: Map<CoverKind, CoverRect> = emptyMap(),
    val visibleCovers: Map<CoverKind, CoverRect> = emptyMap(),
    val audioFocusHeld: Boolean = false,
    /** Detector verdict for the specified caught-up-row visibility threshold. */
    val caughtUpRowReached: Boolean = false,
    val interaction: InstagramInteraction? = null,
    val feedNavigation: FeedNavigationSnapshot? = null,
    /** A failed attempt from a previous check; never an instruction to replay the tap. */
    val failedTap: ActionTarget? = null,
) {
    init { require(replyBoxMissingMs >= 0) }
}

enum class InstagramInteraction {
    REELS_COVER_TAPPED, OPEN_FOLLOWING, OPEN_FAVORITES, OPEN_SEARCH,
    GO_BACK_UP, VIEW_OLDER_POSTS, CLOSE_STORY,
}

enum class FeedDestination { FOLLOWING, FAVORITES, SEARCH, CAUGHT_UP_TOP, OLDER_POSTS }

enum class FeedNavigationStep {
    HOME_TAB, LOGO_MENU, MENU_ENTRY, DESTINATION_CONFIRMATION,
    SEARCH_TAB, SEARCH_BOX, SCROLL_ABOVE_CAUGHT_UP, OLDER_POSTS_LINK, COMPLETE, STOPPED,
}

/**
 * The caller advances steps, records tap/scroll attempts and resets the timer on progress.
 * After NAVIGATION_FAILED the caller must set step to STOPPED, preventing retries/messages.
 */
data class FeedNavigationSnapshot(
    val destination: FeedDestination,
    val step: FeedNavigationStep,
    val msSinceProgress: Long = 0,
    val attemptedTargets: Set<ActionTarget> = emptySet(),
    val menuEntryCount: Int = 0,
    val destinationTitleMatches: Boolean = false,
    /** Result supplied by the executor, including a failed Go back up scroll. */
    val stepFailed: Boolean = false,
) {
    init {
        require(msSinceProgress >= 0)
        require(menuEntryCount >= 0)
    }
}
