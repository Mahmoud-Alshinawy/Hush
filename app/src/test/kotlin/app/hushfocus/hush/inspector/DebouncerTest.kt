package app.hushfocus.hush.inspector

import kotlin.test.Test
import kotlin.test.assertEquals

class DebouncerTest {
    @Test
    fun singleEventFiresAfterTrailingDelay() {
        assertEquals(10_400L, Debouncer().onEvent(10_000L))
    }

    @Test
    fun steadyEventsFireByMaxWait() {
        val debouncer = Debouncer()
        var fireAt = 0L
        for (t in 0L..2_000L step 100L) fireAt = debouncer.onEvent(t)
        assertEquals(1_000L, fireAt)
    }

    @Test
    fun laterEventPushesTrailingDelayWithinMaxWait() {
        val debouncer = Debouncer()
        debouncer.onEvent(0L)
        assertEquals(700L, debouncer.onEvent(300L))
    }

    @Test
    fun afterFiringANewBurstStarts() {
        val debouncer = Debouncer()
        debouncer.onEvent(0L)
        debouncer.onFired()
        assertEquals(5_400L, debouncer.onEvent(5_000L))
    }
}
