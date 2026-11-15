package app.hushfocus.hush.inspector

import org.json.JSONObject
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class InspectorStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store(limits: RollingLimits = RollingLimits()) = InspectorStore(temp.root, limits)

    private fun times(store: InspectorStore) = store.readAll().map { it.timeMs }

    @Test
    fun appendsWithoutTrimmingUntilTenPercentOver() {
        val store = store(RollingLimits(maxCount = 10))
        repeat(11) { store.append(Fixtures.scroll(timeMs = it.toLong())) }
        assertEquals(11, store.readAll().size)

        store.append(Fixtures.scroll(timeMs = 11L))
        assertEquals((2L..11L).toList(), times(store))
    }

    @Test
    fun sizeLimitTrimsOldest() {
        val lineBytes = RollingLimits.lineBytes(InspectorJson.encode(Fixtures.snapshot()))
        val store = store(RollingLimits(maxCount = 300, maxBytes = lineBytes * 3))
        repeat(4) { store.append(Fixtures.snapshot(timeMs = it.toLong())) }
        assertEquals((1L..3L).toList(), times(store))
    }

    @Test
    fun undoRestoresOldEntriesBeforeNewOnes() {
        val store = store()
        store.append(Fixtures.scroll(timeMs = 1L))
        store.append(Fixtures.scroll(timeMs = 2L))
        store.clear()
        assertEquals(emptyList(), times(store))

        store.append(Fixtures.scroll(timeMs = 3L))
        store.undoClear()
        assertEquals(listOf(1L, 2L, 3L), times(store))
    }

    @Test
    fun undoAppliesLimits() {
        val store = store(RollingLimits(maxCount = 3))
        repeat(3) { store.append(Fixtures.scroll(timeMs = it.toLong())) }
        store.clear()
        store.append(Fixtures.scroll(timeMs = 10L))
        store.append(Fixtures.scroll(timeMs = 11L))
        store.undoClear()
        assertEquals(listOf(2L, 10L, 11L), times(store))
    }

    @Test
    fun confirmClearDeletesForGood() {
        val store = store()
        store.append(Fixtures.scroll(timeMs = 1L))
        store.clear()
        store.confirmClear()
        store.undoClear()
        assertEquals(emptyList(), times(store))
        assertEquals(false, File(temp.root, "log.jsonl.undo").exists())
    }

    @Test
    fun exportWritesAllEntries() {
        val store = store()
        store.append(Fixtures.snapshot(timeMs = 1L))
        store.append(Fixtures.scroll(timeMs = 2L))
        val out = ByteArrayOutputStream()
        assertEquals(2, store.exportTo(out, exportedAtMs = 99L))
        val json = JSONObject(out.toString(Charsets.UTF_8))
        assertEquals(2, json.getJSONArray("entries").length())
        assertEquals(99L, json.getLong("exportedAtMs"))
    }

    @Test
    fun countSurvivesNewStoreInstance() {
        val first = store(RollingLimits(maxCount = 10))
        repeat(11) { first.append(Fixtures.scroll(timeMs = it.toLong())) }
        val second = store(RollingLimits(maxCount = 10))
        second.append(Fixtures.scroll(timeMs = 11L))
        assertEquals(10, second.readAll().size)
    }
}
