package app.hushfocus.hush.core

import java.time.ZoneId

/** Serialized, caller-driven updates. All times and executor-owned state are supplied explicitly. */
class InstagramSnapshotAssembler(verticalScrollThresholdFraction: Double, allowanceStore: AllowanceStore) {
    private val reels = ReelSessionTracker(verticalScrollThresholdFraction)
    private val feed = FeedNavigationTracker()
    private val actions = ActionLog()
    private val allowance = AllowanceTracker(allowanceStore)
    private var inReelsTabContext = false
    private var reelsTabRedirectFailed = false

    fun observeScroll(observation: ScrollObservation) { reels.observeScroll(observation) }
    fun observeLink(observation: LinkOpenedObservation) { reels.observeLink(observation) }

    fun recordDecision(decision: PolicyDecision) { feed.recordDecision(decision) }

    fun recordExecution(feedback: ActionExecutionFeedback) {
        actions.recordExecution(feedback)
        feed.recordExecution(feedback)
        if (inReelsTabContext && feedback.action == Action.Tap(ActionTarget.DMS_TAB) && !feedback.succeeded) {
            reelsTabRedirectFailed = true
        }
    }

    fun leaveInstagram(atMs: Long, zoneId: ZoneId) {
        reels.observe(InstagramObservation(atMs, InstagramScreen.UNKNOWN))
        feed.stop()
        allowance.stop(AppId.INSTAGRAM, atMs, zoneId)
        inReelsTabContext = false
        reelsTabRedirectFailed = false
    }

    fun assemble(
        observation: InstagramObservation,
        zoneId: ZoneId,
        settings: InstagramSettings = InstagramSettings(),
        globalSettings: GlobalSettings = GlobalSettings(),
        controls: GlobalControlState = GlobalControlState(),
        allowanceLimitMs: Long? = null,
        executedCovers: ExecutedCoverState = ExecutedCoverState(),
        interaction: InstagramInteraction? = null,
        nowMs: Long = observation.atMs,
    ): PolicySnapshot {
        require(nowMs >= observation.atMs)
        reels.observe(observation)
        if (interaction != null) feed.start(interaction, observation, nowMs)
        feed.observe(observation)
        allowance.observe(AppId.INSTAGRAM, observation.atMs, observation.reelPlayerVisible,
            allowanceLimitMs, zoneId)
        val reelState = reels.snapshot(nowMs)
        inReelsTabContext = when (observation.screen) {
            InstagramScreen.UNKNOWN, InstagramScreen.DM_INBOX, InstagramScreen.DM_CHAT -> false
            else -> observation.screen == InstagramScreen.REELS_TAB || observation.reelsTabSelected ||
                (observation.reelPlayerVisible && reelState.origin == ReelOrigin.REELS_TAB)
        }
        if (!inReelsTabContext) reelsTabRedirectFailed = false

        val instagram = InstagramSnapshot(
            screen = observation.screen,
            settings = settings,
            reel = reelState,
            reelPlayerVisible = observation.reelPlayerVisible,
            storyViewerVisible = observation.storyViewerVisible,
            reelsTabSelected = observation.reelsTabSelected,
            replyBoxVisible = observation.replyBoxVisible,
            replyBoxMissingMs = reels.replyBoxMissingMs(nowMs),
            sheetOrMenuOpen = observation.sheetOrMenuOpen,
            backArrowVisible = observation.backArrowVisible,
            dmComposerVisible = observation.dmComposerVisible,
            tabBarVisible = observation.tabBarVisible,
            allowance = allowance.takeState(AppId.INSTAGRAM, nowMs, zoneId),
            targets = observation.targets,
            coverBounds = observation.coverBounds,
            visibleCovers = executedCovers.visibleCovers,
            audioFocusHeld = executedCovers.audioFocusHeld,
            caughtUpRowReached = observation.caughtUpRowReached,
            interaction = interaction,
            feedNavigation = feed.snapshot(nowMs),
            failedTap = actions.takeFailedTap(),
            reelsTabRedirectFailed = reelsTabRedirectFailed,
        )
        return PolicySnapshot(AppId.INSTAGRAM, globalSettings, controls, instagram,
            actions.msSinceLastNavigationAction(nowMs))
    }
}
