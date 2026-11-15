package app.hushfocus.hush.inspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollingLimitsTest {
    @Test
    fun countLimitDropsOldest() {
        val lines = (1..301).map { "line$it" }
        val trimmed = RollingLimits(maxCount = 300).trim(lines)
        assertEquals(300, trimmed.size)
        assertEquals("line2", trimmed.first())
        assertEquals("line301", trimmed.last())
    }

    @Test
    fun sizeLimitDropsOldest() {
        // Each line is 99 chars + newline = 100 bytes.
        val lines = (1..10).map { "$it".padEnd(99, 'x') }
        val trimmed = RollingLimits(maxCount = 300, maxBytes = 450).trim(lines)
        assertEquals(4, trimmed.size)
        assertEquals(lines.takeLast(4), trimmed)
    }

    @Test
    fun stricterLimitWins() {
        val lines = (1..10).map { "$it".padEnd(99, 'x') }
        assertEquals(3, RollingLimits(maxCount = 3, maxBytes = 450).trim(lines).size)
        assertEquals(2, RollingLimits(maxCount = 5, maxBytes = 250).trim(lines).size)
    }

    @Test
    fun singleOversizedLineIsDropped() {
        val lines = listOf("x".repeat(600))
        assertEquals(emptyList(), RollingLimits(maxCount = 300, maxBytes = 500).trim(lines))
    }

    @Test
    fun withinLimitsNothingChanges() {
        val lines = listOf("a", "b", "c")
        assertEquals(lines, RollingLimits().trim(lines))
    }

    @Test
    fun trimOnlyNeededAboveTenPercentOver() {
        val limits = RollingLimits(maxCount = 300, maxBytes = 1000)
        assertFalse(limits.needsTrim(count = 330, bytes = 1100))
        assertTrue(limits.needsTrim(count = 331, bytes = 0))
        assertTrue(limits.needsTrim(count = 0, bytes = 1101))
    }
}
