package app.hushfocus.hush.inspector.model

/**
 * One line of the Inspector log. Plain data with no Android types, so it can be
 * scrubbed, compared and serialised in JVM unit tests.
 *
 * Every text field here has already been scrubbed (see Scrubber); raw screen text
 * never reaches these classes.
 */
sealed interface InspectorEntry {
    val timeMs: Long
    val packageName: String
}

data class ScreenSnapshot(
    override val timeMs: Long,
    override val packageName: String,
    val trigger: String,
    val windows: List<WindowInfo>,
    val nodes: List<NodeInfo>,
    val nodeCapHit: Boolean,
    val depthCapHit: Boolean,
) : InspectorEntry

/** One or more consecutive scroll events from the same source, merged (see ScrollMerger). */
data class ScrollRecord(
    override val timeMs: Long,
    override val packageName: String,
    val sourceId: String?,
    val sourceClass: String?,
    val bounds: Bounds?,
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    val scrollDeltaX: Int,
    val scrollDeltaY: Int,
    val events: Int = 1,
) : InspectorEntry

data class WindowInfo(
    val type: String,
    val layer: Int,
    val focused: Boolean,
    val active: Boolean,
    val bounds: Bounds,
    val title: String?,
)

/** A node from a depth-first, pre-order walk; [depth] rebuilds the tree. */
data class NodeInfo(
    val depth: Int,
    val viewId: String?,
    val className: String?,
    val bounds: Bounds,
    val childCount: Int,
    val flags: Set<NodeFlag>,
    val text: String? = null,
    val contentDescription: String? = null,
    val hintText: String? = null,
    val paneTitle: String? = null,
    val stateDescription: String? = null,
    val collectionInfo: CollectionInfo? = null,
    val collectionItemInfo: CollectionItemInfo? = null,
)

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class CollectionInfo(val rows: Int, val cols: Int)

data class CollectionItemInfo(val rowIndex: Int, val colIndex: Int)

enum class NodeFlag(val key: String) {
    CLICKABLE("clickable"),
    LONG_CLICKABLE("longClickable"),
    SELECTED("selected"),
    CHECKED("checked"),
    ENABLED("enabled"),
    FOCUSED("focused"),
    SCROLLABLE("scrollable"),
    EDITABLE("editable"),
    VISIBLE("visible"),
    ;

    companion object {
        fun fromKey(key: String): NodeFlag = entries.first { it.key == key }
    }
}
