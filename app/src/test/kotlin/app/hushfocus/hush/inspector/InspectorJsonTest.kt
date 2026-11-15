package app.hushfocus.hush.inspector

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InspectorJsonTest {
    @Test
    fun snapshotRoundTrips() {
        val snapshot = Fixtures.snapshot()
        assertEquals(snapshot, InspectorJson.decode(InspectorJson.encode(snapshot)))
    }

    @Test
    fun scrollRecordRoundTrips() {
        val scroll = Fixtures.scroll(timeMs = 42L).copy(events = 7)
        assertEquals(scroll, InspectorJson.decode(InspectorJson.encode(scroll)))
    }

    @Test
    fun scrollRecordWithoutSourceRoundTrips() {
        val scroll = Fixtures.scroll(timeMs = 42L).copy(sourceId = null, sourceClass = null, bounds = null)
        assertEquals(scroll, InspectorJson.decode(InspectorJson.encode(scroll)))
    }

    @Test
    fun encodedSnapshotIsOneLineAndOmitsNulls() {
        val line = InspectorJson.encode(Fixtures.snapshot())
        assertFalse(line.contains('\n'))
        val root = JSONObject(line).getJSONArray("nodes").getJSONObject(0)
        assertFalse(root.has("id"))
        assertFalse(root.has("text"))
    }
}
