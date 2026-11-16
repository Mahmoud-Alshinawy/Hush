package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverRect

/**
 * A plain copy of what's on screen, so the cover rules can be decided and unit-tested
 * without Android. Built by CoverManager from the accessibility window list.
 */
data class ScreenState(val display: ScreenRect, val windows: List<WindowSnapshot>)

data class WindowSnapshot(
    val type: WindowType,
    val layer: Int,
    val bounds: ScreenRect,
    val active: Boolean,
    val focused: Boolean,
    /** Package of the window's root node; null when it has no root (yet). */
    val ownerPackage: String?,
)

enum class WindowType { APPLICATION, INPUT_METHOD, SYSTEM, ACCESSIBILITY_OVERLAY, OTHER }

/** Screen coordinates. Unlike CoverRect it may be empty: real window lists contain zero-size windows. */
data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(rect: CoverRect): Boolean =
        rect.left >= left && rect.top >= top && rect.right <= right && rect.bottom <= bottom

    /** True when the two share an area larger than zero; touching edges don't count. */
    fun overlaps(rect: CoverRect): Boolean =
        rect.left < right && left < rect.right && rect.top < bottom && top < rect.bottom
}
