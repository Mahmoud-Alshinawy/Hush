package app.hushfocus.hush.inspector

import app.hushfocus.hush.inspector.model.NodeFlag
import app.hushfocus.hush.inspector.model.ScreenSnapshot

/**
 * A hash of a snapshot's structure, used to skip snapshots identical to the previous one.
 * Bounds, text and time are left out, so a playing video or a slightly scrolled list
 * counts as the same screen; a new node, a different view id or a changed
 * selected/checked/enabled state does not.
 */
object StructureSignature {
    private val stateFlags = setOf(NodeFlag.SELECTED, NodeFlag.CHECKED, NodeFlag.ENABLED)

    fun of(snapshot: ScreenSnapshot): Int {
        var hash = snapshot.packageName.hashCode()
        for (window in snapshot.windows) {
            hash = 31 * hash + window.type.hashCode()
        }
        for (node in snapshot.nodes) {
            hash = 31 * hash + node.depth
            hash = 31 * hash + (node.viewId?.hashCode() ?: 0)
            hash = 31 * hash + (node.className?.hashCode() ?: 0)
            hash = 31 * hash + node.flags.intersect(stateFlags).hashCode()
        }
        return hash
    }
}
