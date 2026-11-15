package app.hushfocus.hush.inspector

import app.hushfocus.hush.inspector.model.Bounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StructureSignatureTest {
    private val base = Fixtures.snapshot()

    @Test
    fun identicalSnapshotsMatch() {
        assertEquals(StructureSignature.of(base), StructureSignature.of(Fixtures.snapshot(timeMs = 9_999L)))
    }

    @Test
    fun differentBoundsAndTextStillMatch() {
        val moved = base.copy(
            nodes = base.nodes.map { it.copy(bounds = Bounds(1, 2, 3, 4), text = "‹text:99›") },
        )
        assertEquals(StructureSignature.of(base), StructureSignature.of(moved))
    }

    @Test
    fun addedNodeDiffers() {
        val bigger = base.copy(nodes = base.nodes + base.nodes.last())
        assertNotEquals(StructureSignature.of(base), StructureSignature.of(bigger))
    }

    @Test
    fun changedViewIdDiffers() {
        val renamed = base.copy(nodes = base.nodes.map { it.copy(viewId = "other:id/x") })
        assertNotEquals(StructureSignature.of(base), StructureSignature.of(renamed))
    }

    @Test
    fun changedSelectionDiffers() {
        assertNotEquals(StructureSignature.of(base), StructureSignature.of(Fixtures.snapshot(selectedTab = false)))
    }
}
