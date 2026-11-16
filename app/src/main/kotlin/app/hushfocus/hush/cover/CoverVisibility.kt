package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverRect

/**
 * Where a cover may be placed and when it must stay hidden (spec §3.1.5, §4).
 *
 * The target window is the target app's APPLICATION window with the lowest layer.
 * A rect is placeable only when it lies fully inside both the display and that window;
 * it is never clamped or adjusted. A placed cover is visible only while the target
 * window is active and focused and no keyboard, and no other non-Hush window above
 * the target, overlaps it.
 */
class CoverVisibility(private val hushPackage: String = HUSH_PACKAGE) {

    fun isPlaceable(rect: CoverRect, targetPackage: String, state: ScreenState): Boolean {
        val target = targetWindow(targetPackage, state) ?: return false
        return state.display.contains(rect) && target.bounds.contains(rect)
    }

    fun isVisible(rect: CoverRect, targetPackage: String, state: ScreenState): Boolean {
        val target = targetWindow(targetPackage, state) ?: return false
        if (!target.bounds.contains(rect)) return false
        if (!target.active || !target.focused) return false
        return state.windows.none { window ->
            window !== target &&
                !isHushWindow(window) &&
                window.bounds.overlaps(rect) &&
                (window.type == WindowType.INPUT_METHOD || window.layer > target.layer)
        }
    }

    private fun targetWindow(targetPackage: String, state: ScreenState): WindowSnapshot? =
        state.windows
            .filter { it.type == WindowType.APPLICATION && it.ownerPackage == targetPackage }
            .minByOrNull { it.layer }

    /**
     * Hush's own windows never hide a cover. An accessibility overlay without a root
     * (a cover that is INVISIBLE or not laid out yet) counts as Hush's too; otherwise a
     * cover could hide itself and flicker.
     */
    private fun isHushWindow(window: WindowSnapshot): Boolean =
        window.ownerPackage == hushPackage ||
            (window.type == WindowType.ACCESSIBILITY_OVERLAY && window.ownerPackage == null)

    companion object {
        const val HUSH_PACKAGE = "app.hushfocus.hush"
    }
}
