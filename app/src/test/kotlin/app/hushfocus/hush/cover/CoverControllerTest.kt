package app.hushfocus.hush.cover

import app.hushfocus.hush.core.CoverKind
import app.hushfocus.hush.core.CoverRect
import app.hushfocus.hush.cover.Screens.INSTAGRAM
import app.hushfocus.hush.cover.Screens.app
import app.hushfocus.hush.cover.Screens.keyboard
import app.hushfocus.hush.cover.Screens.screen
import app.hushfocus.hush.cover.Screens.tabRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverControllerTest {
    private class FakeCoverWindows : CoverWindows {
        val calls = mutableListOf<String>()
        val shown = mutableMapOf<CoverKind, Pair<CoverLayout, Boolean>>()

        override fun add(kind: CoverKind, layout: CoverLayout, visible: Boolean) {
            calls += "add $kind"
            shown[kind] = layout to visible
        }

        override fun update(kind: CoverKind, layout: CoverLayout, visible: Boolean) {
            calls += "update $kind"
            shown[kind] = layout to visible
        }

        override fun remove(kind: CoverKind) {
            calls += "remove $kind"
            shown.remove(kind)
        }

        fun count(prefix: String) = calls.count { it.startsWith(prefix) }
    }

    private val fake = FakeCoverWindows()
    private val controller = CoverController(fake)
    private val normal = screen(app())
    private val feedRect = CoverRect(0, 400, 1080, 2200)

    @Test
    fun sameShowTwiceAddsOnce() {
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        assertEquals(1, fake.count("add"))
        assertEquals(0, fake.count("update"))
        assertEquals(true, fake.shown[CoverKind.REELS_TAB]?.second)
    }

    @Test
    fun newRectUpdatesWithoutRemove() {
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, CoverRect(216, 2211, 432, 2337), normal)
        assertEquals(1, fake.count("update"))
        assertEquals(0, fake.count("remove"))
        assertEquals(CoverLayout(216, 2211, 216, 126), fake.shown[CoverKind.REELS_TAB]?.first)
    }

    @Test
    fun rejectedRectRemovesExistingCover() {
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, CoverRect(900, 2211, 1200, 2337), normal)
        assertEquals(listOf("add REELS_TAB", "remove REELS_TAB"), fake.calls)
        assertFalse(controller.hasCovers)
    }

    @Test
    fun rejectedRectForNewKindAddsNothing() {
        controller.show(INSTAGRAM, CoverKind.FEED, CoverRect(0, 0, 1080, 2500), normal)
        assertEquals(emptyList(), fake.calls)
    }

    @Test
    fun hideAllRemovesEverythingAndIsSafeTwice() {
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        controller.show(INSTAGRAM, CoverKind.FEED, feedRect, normal)
        controller.hideAll()
        assertEquals(2, fake.count("remove"))
        assertTrue(fake.shown.isEmpty())

        controller.hideAll()
        assertEquals(2, fake.count("remove"))
        assertFalse(controller.hasCovers)
    }

    @Test
    fun hideRemovesOnlyThatKind() {
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        controller.show(INSTAGRAM, CoverKind.FEED, feedRect, normal)
        controller.hide(CoverKind.FEED)
        controller.hide(CoverKind.FEED)
        assertEquals(listOf("remove FEED"), fake.calls.filter { it.startsWith("remove") })
        assertEquals(setOf(CoverKind.REELS_TAB), fake.shown.keys)
    }

    @Test
    fun removeAllForOnlyRemovesThatApp() {
        val youtube = "com.google.android.youtube"
        val both = screen(app(), app(owner = youtube, layer = -1))
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, both)
        controller.show(youtube, CoverKind.FEED, feedRect, both)
        controller.removeAllFor(INSTAGRAM)
        assertEquals(setOf(CoverKind.FEED), fake.shown.keys)
    }

    @Test
    fun keyboardHidesThenShowsAgainWithUpdatesOnly() {
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        controller.onWindowsChanged(screen(app(), keyboard(ScreenRect(0, 1500, 1080, 2400))))
        assertEquals(false, fake.shown[CoverKind.REELS_TAB]?.second)
        controller.onWindowsChanged(screen(app(), keyboard(ScreenRect(0, 1500, 1080, 2400))))
        controller.onWindowsChanged(normal)
        assertEquals(true, fake.shown[CoverKind.REELS_TAB]?.second)
        assertEquals(listOf("add REELS_TAB", "update REELS_TAB", "update REELS_TAB"), fake.calls)
    }

    @Test
    fun unchangedWindowsMakeNoCalls() {
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        repeat(5) { controller.onWindowsChanged(normal) }
        assertEquals(listOf("add REELS_TAB"), fake.calls)
    }

    @Test
    fun rotationHidesAllUntilFreshShow() {
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        controller.show(INSTAGRAM, CoverKind.FEED, feedRect, normal)
        controller.onDisplayChanged()
        assertTrue(fake.shown.values.none { it.second })
        assertEquals(0, fake.count("remove"))

        // Windows changing doesn't bring a stale cover back.
        controller.onWindowsChanged(normal)
        assertTrue(fake.shown.values.none { it.second })

        // A fresh show, even with the same rect, does.
        controller.show(INSTAGRAM, CoverKind.REELS_TAB, tabRect, normal)
        assertEquals(true, fake.shown[CoverKind.REELS_TAB]?.second)
        assertEquals(false, fake.shown[CoverKind.FEED]?.second)
    }
}
