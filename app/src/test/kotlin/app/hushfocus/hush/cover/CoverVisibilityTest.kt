package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverRect
import app.hushfocus.hush.cover.Screens.INSTAGRAM
import app.hushfocus.hush.cover.Screens.app
import app.hushfocus.hush.cover.Screens.keyboard
import app.hushfocus.hush.cover.Screens.overlay
import app.hushfocus.hush.cover.Screens.screen
import app.hushfocus.hush.cover.Screens.statusBar
import app.hushfocus.hush.cover.Screens.tabRect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverVisibilityTest {
    private val rules = CoverVisibility()

    private fun visible(state: ScreenState, rect: CoverRect = tabRect) = rules.isVisible(rect, INSTAGRAM, state)

    @Test
    fun plainAppScreenShowsCover() {
        assertTrue(visible(screen(app(), statusBar())))
    }

    @Test
    fun keyboardOverlappingHides() {
        assertFalse(visible(screen(app(), keyboard(ScreenRect(0, 1500, 1080, 2400)))))
    }

    @Test
    fun keyboardElsewhereShows() {
        assertTrue(visible(screen(app(), keyboard(ScreenRect(0, 1500, 1080, 2000)))))
    }

    @Test
    fun keyboardTouchingEdgeOnlyShows() {
        assertTrue(visible(screen(app(), keyboard(ScreenRect(0, 1500, 1080, 2211)))))
    }

    @Test
    fun sameAppDialogAboveOverlappingHides() {
        val dialog = app(active = false, focused = false, bounds = ScreenRect(0, 1800, 1080, 2400), layer = 1)
        assertFalse(visible(screen(app(), dialog)))
    }

    @Test
    fun otherAppWindowAboveOverlappingHides() {
        val bubble = app(owner = "com.example.chatheads", bounds = ScreenRect(400, 2000, 700, 2300), layer = 3)
        assertFalse(visible(screen(app(), bubble)))
    }

    @Test
    fun otherWindowBelowTargetShows() {
        val below = app(owner = "com.android.launcher", layer = -1, active = false, focused = false)
        assertTrue(visible(screen(app(), below)))
    }

    @Test
    fun hushOverlayOverlappingShows() {
        assertTrue(visible(screen(app(), overlay(ScreenRect(432, 2211, 648, 2337), owner = Screens.HUSH))))
    }

    @Test
    fun hushOverlayWithoutRootOverlappingShows() {
        assertTrue(visible(screen(app(), overlay(ScreenRect(432, 2211, 648, 2337), owner = null))))
    }

    @Test
    fun otherServicesOverlayOverlappingHides() {
        assertFalse(visible(screen(app(), overlay(ScreenRect(0, 2000, 1080, 2400), owner = "com.example.a11y"))))
    }

    @Test
    fun targetNotActiveHides() {
        assertFalse(visible(screen(app(active = false))))
    }

    @Test
    fun targetNotFocusedHides() {
        assertFalse(visible(screen(app(focused = false))))
    }

    @Test
    fun noTargetWindowHides() {
        assertFalse(visible(screen(app(owner = "com.example.other"))))
    }

    @Test
    fun rectPartlyOutsideWindowIsRejected() {
        val window = app(bounds = ScreenRect(0, 0, 1080, 2300))
        assertFalse(rules.isPlaceable(tabRect, INSTAGRAM, screen(window)))
        assertFalse(visible(screen(window)))
    }

    @Test
    fun rectPartlyOutsideDisplayIsRejected() {
        val wide = app(bounds = ScreenRect(-100, 0, 1180, 2400))
        assertFalse(rules.isPlaceable(CoverRect(-50, 2211, 200, 2337), INSTAGRAM, screen(wide)))
    }

    @Test
    fun rectOnWindowEdgesIsPlaceable() {
        assertTrue(rules.isPlaceable(CoverRect(0, 0, 1080, 2400), INSTAGRAM, screen(app())))
    }

    @Test
    fun noTargetWindowIsNotPlaceable() {
        assertFalse(rules.isPlaceable(tabRect, INSTAGRAM, screen(statusBar())))
    }
}
