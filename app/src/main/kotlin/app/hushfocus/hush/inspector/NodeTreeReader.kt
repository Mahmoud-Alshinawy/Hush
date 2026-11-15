package app.hushfocus.hush.inspector

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.hushfocus.hush.inspector.model.Bounds
import app.hushfocus.hush.inspector.model.CollectionInfo
import app.hushfocus.hush.inspector.model.CollectionItemInfo
import app.hushfocus.hush.inspector.model.NodeFlag
import app.hushfocus.hush.inspector.model.NodeInfo
import app.hushfocus.hush.inspector.model.ScreenSnapshot
import app.hushfocus.hush.inspector.model.WindowInfo
import app.hushfocus.hush.service.SupportedApps

/**
 * Reads the window list and the active window's node tree into a ScreenSnapshot.
 *
 * Every text value is scrubbed the moment it is read: raw screen text exists only as
 * a local value inside one call and never reaches a model object, logcat or disk.
 * The walk is depth-first pre-order, capped at [maxNodes] nodes and [maxDepth] levels.
 */
class NodeTreeReader(
    private val maxNodes: Int = 3_000,
    private val maxDepth: Int = 60,
) {
    /** Returns null when the active window doesn't belong to a supported app. */
    fun read(service: AccessibilityService, trigger: String, timeMs: Long): ScreenSnapshot? {
        InspectorCounters.treeRead()
        val root = service.rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString() ?: return null
        if (packageName !in SupportedApps.packages) return null
        val labels = LabelAllowList.labelsFor(packageName)

        val windows = service.windows.map { readWindow(it, labels) }

        val nodes = ArrayList<NodeInfo>()
        var nodeCapHit = false
        var depthCapHit = false
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            if (nodes.size >= maxNodes) {
                nodeCapHit = true
                break
            }
            val (node, depth) = stack.removeLast()
            nodes += readNode(node, depth, labels)
            if (node.childCount == 0) continue
            if (depth + 1 >= maxDepth) {
                depthCapHit = true
                continue
            }
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
        }

        return ScreenSnapshot(timeMs, packageName, trigger, windows, nodes, nodeCapHit, depthCapHit)
    }

    private fun readWindow(window: AccessibilityWindowInfo, labels: Set<String>) = WindowInfo(
        type = windowTypeName(window.type),
        layer = window.layer,
        focused = window.isFocused,
        active = window.isActive,
        bounds = Rect().also(window::getBoundsInScreen).toBounds(),
        title = Scrubber.scrub(window.title?.toString(), labels),
    )

    private fun readNode(node: AccessibilityNodeInfo, depth: Int, labels: Set<String>) = NodeInfo(
        depth = depth,
        viewId = node.viewIdResourceName,
        className = node.className?.toString(),
        bounds = Rect().also(node::getBoundsInScreen).toBounds(),
        childCount = node.childCount,
        flags = flagsOf(node),
        text = Scrubber.scrub(node.text?.toString(), labels),
        contentDescription = Scrubber.scrub(node.contentDescription?.toString(), labels),
        hintText = Scrubber.scrub(node.hintText?.toString(), labels),
        paneTitle = Scrubber.scrub(node.paneTitle?.toString(), labels),
        stateDescription = Scrubber.scrub(node.stateDescription?.toString(), labels),
        collectionInfo = node.collectionInfo?.let { CollectionInfo(it.rowCount, it.columnCount) },
        collectionItemInfo = node.collectionItemInfo?.let { CollectionItemInfo(it.rowIndex, it.columnIndex) },
    )

    private fun flagsOf(node: AccessibilityNodeInfo): Set<NodeFlag> = buildSet {
        if (node.isClickable) add(NodeFlag.CLICKABLE)
        if (node.isLongClickable) add(NodeFlag.LONG_CLICKABLE)
        if (node.isSelected) add(NodeFlag.SELECTED)
        if (isChecked(node)) add(NodeFlag.CHECKED)
        if (node.isEnabled) add(NodeFlag.ENABLED)
        if (node.isFocused) add(NodeFlag.FOCUSED)
        if (node.isScrollable) add(NodeFlag.SCROLLABLE)
        if (node.isEditable) add(NodeFlag.EDITABLE)
        if (node.isVisibleToUser) add(NodeFlag.VISIBLE)
    }

    /** API 36 replaced the boolean isChecked with a tri-state getChecked(). */
    private fun isChecked(node: AccessibilityNodeInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            node.checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE
        } else {
            @Suppress("DEPRECATION")
            node.isChecked
        }

    private fun windowTypeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "application"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "inputMethod"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "accessibilityOverlay"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "splitScreenDivider"
        AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> "magnificationOverlay"
        else -> "type$type"
    }
}

internal fun Rect.toBounds() = Bounds(left, top, right, bottom)
