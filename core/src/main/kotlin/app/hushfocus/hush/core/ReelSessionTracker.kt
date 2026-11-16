package app.hushfocus.hush.core

import kotlin.math.abs

/** A sheet is not an absence; absence starts with the first non-player observation. */
class ReelSessionTracker(private val verticalScrollThresholdFraction: Double) {
    init { require(verticalScrollThresholdFraction.isFinite() && verticalScrollThresholdFraction > 0) }

    private data class OriginEvidence(val origin: ReelOrigin, val atMs: Long)
    private class Session(val openedAtMs: Long, var snapshot: ReelSessionSnapshot) {
        var absentSinceMs: Long? = null
        var pageIndex: Int? = null
        var author: AuthorKey? = null
        var reelsTabSelected = false
        var playerVisible = true
        var sheetOpen = false
        var lastVerticalScrollAtMs: Long? = null
        var scrollHeightPx = 0
        var netScrollPx = 0L
    }

    private var screenEvidence: OriginEvidence? = null
    private var linkEvidence: OriginEvidence? = null
    private var session: Session? = null

    fun observeLink(observation: LinkOpenedObservation) {
        linkEvidence = OriginEvidence(ReelOrigin.LINK, observation.atMs)
    }

    fun observe(observation: InstagramObservation) {
        expireSession(observation.atMs)
        if (!observation.reelPlayerVisible) {
            session?.let {
                it.playerVisible = false
                if (it.absentSinceMs == null) it.absentSinceMs = observation.atMs
            }
            rememberScreen(observation)
            return
        }

        val current = session ?: openSession(observation).also { session = it }
        current.absentSinceMs = null
        current.playerVisible = true
        current.sheetOpen = observation.sheetOrMenuOpen
        resolveOpening(current, observation.atMs, observation.replyBoxVisible)
        if (current.sheetOpen) return

        observeIndex(current, observation.pageIndex, observation.atMs)
        if (observation.authorKeys.size == 1) {
            val author = observation.authorKeys.single()
            if (current.author != null && current.author != author) emit(current, ReelChange.AUTHOR, observation.atMs)
            current.author = author
        }
        if (!current.reelsTabSelected && observation.reelsTabSelected) {
            emit(current, ReelChange.REELS_TAB_SELECTED, observation.atMs)
        }
        current.reelsTabSelected = observation.reelsTabSelected
    }

    fun observeScroll(observation: ScrollObservation) {
        expireSession(observation.atMs)
        val current = session ?: return
        if (!current.playerVisible || current.sheetOpen || observation.source != SwipeSource.REEL_PAGER ||
            abs(observation.deltaY.toLong()) < abs(observation.deltaX.toLong())
        ) return

        observeIndex(current, observation.fromIndex, observation.atMs)
        if (observation.deltaY == 0 || observation.sourceHeightPx == 0) return
        val previousAt = current.lastVerticalScrollAtMs
        if (previousAt == null || observation.atMs - previousAt > 1_000 ||
            current.scrollHeightPx != observation.sourceHeightPx
        ) current.netScrollPx = 0
        current.lastVerticalScrollAtMs = observation.atMs
        current.scrollHeightPx = observation.sourceHeightPx
        current.netScrollPx += observation.deltaY.toLong()
        if (abs(current.netScrollPx) >= observation.sourceHeightPx * verticalScrollThresholdFraction) {
            emit(current, ReelChange.VERTICAL_SCROLL_THRESHOLD, observation.atMs)
        }
    }

    fun snapshot(nowMs: Long): ReelSessionSnapshot {
        expireSession(nowMs)
        val current = session ?: return ReelSessionSnapshot()
        resolveOpening(current, nowMs, replyBoxVisible = false)
        return current.snapshot
    }

    fun replyBoxMissingMs(nowMs: Long): Long {
        val snapshot = snapshot(nowMs)
        val current = session ?: return 0
        return if (snapshot.origin == ReelOrigin.UNRESOLVED) {
            (nowMs - current.openedAtMs).coerceIn(0, 600)
        } else 0
    }

    private fun openSession(observation: InstagramObservation): Session {
        val evidence = listOfNotNull(screenEvidence, linkEvidence).maxByOrNull { it.atMs }
        val age = evidence?.let { (observation.atMs - it.atMs).coerceAtLeast(0) }
        val origin = when {
            observation.replyBoxVisible -> ReelOrigin.DM
            evidence == null || age == null -> ReelOrigin.UNRESOLVED
            evidence.origin == ReelOrigin.LINK ->
                if (observation.usageAccessGranted && age <= 5_000) ReelOrigin.LINK else ReelOrigin.UNRESOLVED
            evidence.origin == ReelOrigin.PROFILE ->
                if (age <= 1_500) ReelOrigin.PROFILE else ReelOrigin.UNRESOLVED
            evidence.origin == ReelOrigin.STORY ->
                if (age <= 1_500 && !observation.reelsTabSelected) ReelOrigin.STORY else ReelOrigin.UNRESOLVED
            else -> evidence.origin
        }
        return Session(observation.atMs, ReelSessionSnapshot(
            origin = origin,
            originAgeAtOpenMs = age,
            msFromOriginToPlayer = if (origin == ReelOrigin.LINK) null else age,
            usageAccessGranted = observation.usageAccessGranted,
            originResolutionComplete = origin != ReelOrigin.UNRESOLVED,
        )).also { it.reelsTabSelected = observation.reelsTabSelected }
    }

    private fun resolveOpening(current: Session, nowMs: Long, replyBoxVisible: Boolean) {
        if (current.snapshot.originResolutionComplete) return
        if (nowMs - current.openedAtMs >= 600) {
            current.snapshot = current.snapshot.copy(originResolutionComplete = true)
        } else if (replyBoxVisible) {
            current.snapshot = current.snapshot.copy(origin = ReelOrigin.DM, originResolutionComplete = true)
        }
    }

    private fun expireSession(nowMs: Long) {
        val absentAt = session?.absentSinceMs ?: return
        if (nowMs - absentAt >= 1_500) session = null
    }

    private fun observeIndex(current: Session, index: Int?, atMs: Long) {
        if (index == null) return
        if (current.pageIndex != null && index != current.pageIndex) emit(current, ReelChange.PAGE_INDEX, atMs)
        current.pageIndex = index
    }

    private fun emit(current: Session, change: ReelChange, atMs: Long) {
        // Two index/author changes suffice to exceed loading grace; other signals need one.
        val maximum = if (change == ReelChange.PAGE_INDEX || change == ReelChange.AUTHOR) 2 else 1
        if (current.snapshot.changes.count { it.change == change } >= maximum) return
        current.snapshot = current.snapshot.copy(changes = current.snapshot.changes + ReelSwipeEvidence(
            SwipeSource.REEL_PAGER, change, (atMs - current.openedAtMs).coerceAtLeast(0),
        ))
    }

    private fun rememberScreen(observation: InstagramObservation) {
        val origin = when (observation.screen) {
            InstagramScreen.PROFILE -> ReelOrigin.PROFILE
            InstagramScreen.STORY_VIEWER -> ReelOrigin.STORY
            InstagramScreen.HOME_FEED -> ReelOrigin.HOME_FEED
            InstagramScreen.EXPLORE -> ReelOrigin.EXPLORE
            InstagramScreen.SEARCH_RESULTS -> ReelOrigin.SEARCH_RESULTS
            InstagramScreen.FAVORITES -> ReelOrigin.FAVORITES
            InstagramScreen.REELS_TAB -> ReelOrigin.REELS_TAB
            InstagramScreen.DM_CHAT, InstagramScreen.DM_INBOX, InstagramScreen.FOLLOWING -> ReelOrigin.UNRESOLVED
            else -> return
        }
        screenEvidence = OriginEvidence(origin, observation.atMs)
    }
}
