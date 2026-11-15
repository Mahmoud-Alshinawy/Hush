package app.hushfocus.hush.core

sealed interface Action {
    data object Allow : Action
    data object Back : Action
    data class Tap(val target: ActionTarget) : Action
    data class ShowCover(val kind: CoverKind, val rect: CoverRect) : Action
    data class HideCover(val kind: CoverKind) : Action
    data object HideAllCovers : Action
    data class ShowBlockScreen(val reason: BlockReason) : Action
    data object RequestAudioFocus : Action
    data object ReleaseAudioFocus : Action
    data class ScrollTo(val target: ActionTarget) : Action
    data class ShowMessage(val message: HushMessage) : Action
}

/** Menu-entry targets mean positions 1 (Following) and 2 (Favorites), not label matches. */
enum class ActionTarget {
    DMS_TAB, HOME_TAB, INSTAGRAM_LOGO, FOLLOWING_MENU_ENTRY, FAVORITES_MENU_ENTRY,
    SEARCH_TAB, SEARCH_BOX, OLDER_POSTS_LINK, FEED_ABOVE_CAUGHT_UP,
}

enum class CoverKind { REELS_TAB, FEED, CAUGHT_UP, EXPLORE, STORIES_ROW, STORY_VIEWER }

/** Coordinates supplied only after the app's rules have validated the target and bounds. */
data class CoverRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init { require(right > left && bottom > top) }
}

enum class BlockReason { SLEEP, DOWNTIME, DAILY_APP_LIMIT }

enum class HushMessage { REELS_BLOCKED, ALLOWANCE_USED_UP, NAVIGATION_FAILED }

internal fun Action.isNavigation(): Boolean = this == Action.Back || this is Action.Tap
