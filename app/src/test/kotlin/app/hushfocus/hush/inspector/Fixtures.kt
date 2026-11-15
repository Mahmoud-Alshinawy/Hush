package app.hushfocus.hush.inspector

import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.CollectionInfo
import app.hushfocus.hush.inspector.model.CollectionItemInfo
import app.hushfocus.hush.inspector.model.NodeFlag
import app.hushfocus.hush.inspector.model.NodeInfo
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import app.hushfocus.hush.inspector.model.ScrollRecord
import app.hushfocus.hush.inspector.model.WindowInfo

/** Small hand-made entries shared by the Inspector tests. */
object Fixtures {
    fun snapshot(timeMs: Long = 1_000L, selectedTab: Boolean = true): ScreenSnapshot = ScreenSnapshot(
        timeMs = timeMs,
        packageName = "com.google.android.youtube",
        trigger = "windowStateChanged",
        windows = listOf(
            WindowInfo("application", 3, focused = true, active = true, Bounds(0, 0, 1080, 2400), "‹text:7›"),
        ),
        nodes = listOf(
            NodeInfo(0, null, "android.widget.FrameLayout", Bounds(0, 0, 1080, 2400), 1, setOf(NodeFlag.ENABLED, NodeFlag.VISIBLE)),
            NodeInfo(
                depth = 1,
                viewId = "com.google.android.youtube:id/pivot_bar",
                className = "android.widget.LinearLayout",
                bounds = Bounds(0, 2200, 1080, 2337),
                childCount = 1,
                flags = setOf(NodeFlag.ENABLED, NodeFlag.VISIBLE),
                collectionInfo = CollectionInfo(rows = 1, cols = 5),
            ),
            NodeInfo(
                depth = 2,
                viewId = "com.google.android.youtube:id/text",
                className = "android.widget.TextView",
                bounds = Bounds(216, 2290, 432, 2330),
                childCount = 0,
                flags = buildSet {
                    add(NodeFlag.ENABLED)
                    add(NodeFlag.VISIBLE)
                    add(NodeFlag.CLICKABLE)
                    if (selectedTab) add(NodeFlag.SELECTED)
                },
                text = "Shorts",
                contentDescription = "‹text:11›",
                hintText = "‹text:3›",
                paneTitle = "‹text:4›",
                stateDescription = "‹text:2›",
                collectionItemInfo = CollectionItemInfo(rowIndex = 0, colIndex = 1),
            ),
        ),
        nodeCapHit = false,
        depthCapHit = true,
    )

    fun scroll(timeMs: Long, deltaY: Int = 100, fromIndex: Int = 0, toIndex: Int = 3): ScrollRecord = ScrollRecord(
        timeMs = timeMs,
        packageName = "com.google.android.youtube",
        sourceId = "com.google.android.youtube:id/results",
        sourceClass = "androidx.recyclerview.widget.RecyclerView",
        bounds = Bounds(0, 200, 1080, 2200),
        fromIndex = fromIndex,
        toIndex = toIndex,
        itemCount = 40,
        scrollDeltaX = 0,
        scrollDeltaY = deltaY,
    )
}
