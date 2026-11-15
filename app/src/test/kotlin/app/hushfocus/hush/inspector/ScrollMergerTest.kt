package app.hushfocus.hush.inspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrollMergerTest {
    @Test
    fun tenScrollsWithin500msBecomeOneRecord() {
        val merger = ScrollMerger()
        val emitted = (0 until 10).mapNotNull { i ->
            merger.add(Fixtures.scroll(timeMs = 1_000L + i * 50L, deltaY = 100, fromIndex = i, toIndex = i + 3))
        }
        assertEquals(emptyList(), emitted)

        val merged = merger.flush()!!
        assertEquals(10, merged.events)
        assertEquals(0, merged.fromIndex)
        assertEquals(12, merged.toIndex)
        assertEquals(1_000, merged.scrollDeltaY)
        assertEquals(1_000L, merged.timeMs)
        assertNull(merger.flush())
    }

    @Test
    fun scrollAfterWindowStartsNewRecord() {
        val merger = ScrollMerger()
        assertNull(merger.add(Fixtures.scroll(timeMs = 0L)))
        val first = merger.add(Fixtures.scroll(timeMs = 501L))
        assertEquals(1, first?.events)
        assertEquals(501L, merger.flush()?.timeMs)
    }

    @Test
    fun differentSourceStartsNewRecord() {
        val merger = ScrollMerger()
        merger.add(Fixtures.scroll(timeMs = 0L))
        val first = merger.add(Fixtures.scroll(timeMs = 10L).copy(sourceId = "other:id/list"))
        assertEquals(0L, first?.timeMs)
        assertEquals("other:id/list", merger.flush()?.sourceId)
    }

    @Test
    fun windowEndFollowsFirstEvent() {
        val merger = ScrollMerger()
        assertNull(merger.windowEndMs())
        merger.add(Fixtures.scroll(timeMs = 2_000L))
        merger.add(Fixtures.scroll(timeMs = 2_300L))
        assertEquals(2_500L, merger.windowEndMs())
    }
}
