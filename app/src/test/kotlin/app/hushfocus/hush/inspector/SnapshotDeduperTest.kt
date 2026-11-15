package app.hushfocus.hush.inspector

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotDeduperTest {
    @Test
    fun identicalStructureIsSkipped() {
        val deduper = SnapshotDeduper()
        assertTrue(deduper.isNew(Fixtures.snapshot(timeMs = 1L)))
        assertFalse(deduper.isNew(Fixtures.snapshot(timeMs = 2L)))
    }

    @Test
    fun changedStructureIsKept() {
        val deduper = SnapshotDeduper()
        assertTrue(deduper.isNew(Fixtures.snapshot(selectedTab = true)))
        assertTrue(deduper.isNew(Fixtures.snapshot(selectedTab = false)))
        assertTrue(deduper.isNew(Fixtures.snapshot(selectedTab = true)))
    }

    @Test
    fun packagesAreTrackedSeparately() {
        val deduper = SnapshotDeduper()
        val youtube = Fixtures.snapshot()
        val other = youtube.copy(packageName = "com.instagram.android")
        assertTrue(deduper.isNew(youtube))
        assertTrue(deduper.isNew(other))
        assertFalse(deduper.isNew(youtube.copy(timeMs = 5L)))
    }
}
