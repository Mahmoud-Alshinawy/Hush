package app.hushfocus.hush.core

/** An opaque caller-created identity, never a username or other visible text. Memory only. */
class AuthorKey(private val opaqueValue: String) {
    override fun equals(other: Any?): Boolean = other is AuthorKey && opaqueValue == other.opaqueValue
    override fun hashCode(): Int = opaqueValue.hashCode()
    override fun toString(): String = "AuthorKey(redacted)"
}

/**
 * Detector results only. Page index and author labels must come from the reel pager.
 * An underlying player remains visible while its sheet is open. Author labels are a list,
 * not a set: two visible labels with the same key still do not satisfy exactly-one-author.
 */
data class InstagramObservation(
    val atMs: Long,
    val screen: InstagramScreen,
    val reelPlayerVisible: Boolean = screen == InstagramScreen.REEL_PLAYER,
    val storyViewerVisible: Boolean = screen == InstagramScreen.STORY_VIEWER,
    val reelsTabSelected: Boolean = false,
    val replyBoxVisible: Boolean = false,
    val sheetOrMenuOpen: Boolean = false,
    val tabBarVisible: Boolean = false,
    val backArrowVisible: Boolean = false,
    val dmComposerVisible: Boolean = false,
    val pageIndex: Int? = null,
    val authorKeys: List<AuthorKey> = emptyList(),
    val usageAccessGranted: Boolean = false,
    val targets: Set<ActionTarget> = emptySet(),
    val coverBounds: Map<CoverKind, CoverRect> = emptyMap(),
    val caughtUpRowReached: Boolean = false,
    /** Entry count of the verified logo menu; unrelated sheets must report zero. */
    val menuEntryCount: Int = 0,
    val destinationTitleMatches: Boolean = false,
    val searchBoxFocused: Boolean = false,
    val scrollCompleted: Boolean = false,
    val olderPostsOpened: Boolean = false,
) {
    init {
        require(pageIndex == null || pageIndex >= 0)
        require(menuEntryCount >= 0)
    }
}

data class ScrollObservation(
    val atMs: Long,
    val source: SwipeSource,
    val deltaY: Int,
    val deltaX: Int,
    val sourceHeightPx: Int,
    val fromIndex: Int? = null,
) {
    init {
        require(sourceHeightPx >= 0)
        require(fromIndex == null || fromIndex >= 0)
    }
}

data class LinkOpenedObservation(val atMs: Long)
