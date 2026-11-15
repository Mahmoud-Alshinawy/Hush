package app.hushfocus.hush.core

enum class AppId { INSTAGRAM, YOUTUBE, TIKTOK, X, SNAPCHAT, FACEBOOK }

enum class InstagramScreen {
    HOME_FEED, FOLLOWING, FAVORITES, REELS_TAB, EXPLORE, SEARCH_RESULTS,
    REEL_PLAYER, STORY_VIEWER, DM_INBOX, DM_CHAT, PROFILE, OVERLAY_UI, UNKNOWN,
}

enum class ReelOrigin {
    DM, PROFILE, STORY, LINK, HOME_FEED, EXPLORE, SEARCH_RESULTS, FAVORITES,
    REELS_TAB, UNRESOLVED,
}

enum class HomeFeedMode { NORMAL, HIDDEN }

enum class SwipeSource { REEL_PAGER, OTHER_CONTENT }

enum class ReelChange {
    PAGE_INDEX, VERTICAL_SCROLL_THRESHOLD, AUTHOR, REELS_TAB_SELECTED,
}

/** Evidence retained for the current session; thresholds/accumulation belong to rules/trackers. */
data class ReelSwipeEvidence(
    val source: SwipeSource,
    val change: ReelChange,
    val msSincePlayerOpened: Long,
) {
    init { require(msSincePlayerOpened >= 0) }
}
