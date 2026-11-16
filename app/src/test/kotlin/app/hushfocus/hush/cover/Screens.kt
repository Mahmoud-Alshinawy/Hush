package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverRect

/** Hand-made screens for the cover tests: Instagram in front on a 1080×2400 display. */
object Screens {
    const val INSTAGRAM = "com.instagram.android"
    const val HUSH = CoverVisibility.HUSH_PACKAGE

    val display = ScreenRect(0, 0, 1080, 2400)

    /** A Reels-tab-sized cover in the bottom tab bar. */
    val tabRect = CoverRect(432, 2211, 648, 2337)

    fun app(
        active: Boolean = true,
        focused: Boolean = true,
        bounds: ScreenRect = display,
        owner: String = INSTAGRAM,
        layer: Int = 0,
    ) = WindowSnapshot(WindowType.APPLICATION, layer, bounds, active, focused, owner)

    fun keyboard(bounds: ScreenRect) =
        WindowSnapshot(WindowType.INPUT_METHOD, 2, bounds, active = false, focused = false, "com.google.android.inputmethod.latin")

    fun overlay(bounds: ScreenRect, owner: String?) =
        WindowSnapshot(WindowType.ACCESSIBILITY_OVERLAY, 5, bounds, active = false, focused = false, owner)

    fun statusBar() = WindowSnapshot(WindowType.SYSTEM, 1, ScreenRect(0, 0, 1080, 132), false, false, "com.android.systemui")

    fun screen(vararg windows: WindowSnapshot) = ScreenState(display, windows.toList())
}
