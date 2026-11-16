package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverKind
import app.hushfocus.hush.core.CoverRect

/**
 * Decides every cover window operation; pure Kotlin so it can be unit-tested.
 *
 * One window per CoverKind. Showing the same rect again does nothing; a new rect moves
 * the window (update, never remove + add). A rect that isn't placeable means no cover.
 * Hide rules keep the window but hide it. After a rotation or display change all covers
 * are hidden and marked stale until a fresh show, since the old rects no longer match.
 */
class CoverController(
    private val windows: CoverWindows,
    private val visibility: CoverVisibility = CoverVisibility(),
) {
    private data class Cover(
        val targetPackage: String,
        val rect: CoverRect,
        val visible: Boolean,
        val stale: Boolean = false,
    )

    private val covers = LinkedHashMap<CoverKind, Cover>()

    val hasCovers: Boolean get() = covers.isNotEmpty()

    fun show(targetPackage: String, kind: CoverKind, rect: CoverRect, state: ScreenState) {
        if (!visibility.isPlaceable(rect, targetPackage, state)) {
            hide(kind)
            return
        }
        val existing = covers[kind]
        val next = Cover(targetPackage, rect, visibility.isVisible(rect, targetPackage, state))
        when {
            existing == null -> windows.add(kind, rect.toLayout(), next.visible)
            existing == next -> return
            else -> windows.update(kind, rect.toLayout(), next.visible)
        }
        covers[kind] = next
    }

    fun hide(kind: CoverKind) {
        if (covers.remove(kind) != null) windows.remove(kind)
    }

    fun hideAll() {
        covers.keys.toList().forEach(::hide)
    }

    fun removeAllFor(targetPackage: String) {
        covers.filterValues { it.targetPackage == targetPackage }.keys.toList().forEach(::hide)
    }

    fun onWindowsChanged(state: ScreenState) {
        for ((kind, cover) in covers.entries.toList()) {
            if (cover.stale) continue
            val visible = visibility.isVisible(cover.rect, cover.targetPackage, state)
            if (visible != cover.visible) {
                windows.update(kind, cover.rect.toLayout(), visible)
                covers[kind] = cover.copy(visible = visible)
            }
        }
    }

    fun onDisplayChanged() {
        for ((kind, cover) in covers.entries.toList()) {
            if (cover.visible) windows.update(kind, cover.rect.toLayout(), visible = false)
            covers[kind] = cover.copy(visible = false, stale = true)
        }
    }
}
