package app.hushfocus.hush.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllowanceTrackerTest {
    private val time = FakeTime()
    private val store = InMemoryAllowanceStore()
    private val tracker = AllowanceTracker(store)

    private fun observe(visible: Boolean = true, limit: Long? = 10_000, app: AppId = AppId.INSTAGRAM) {
        tracker.observe(app, time.nowMs, visible, limit, time.zoneId)
    }
    private fun state(app: AppId = AppId.INSTAGRAM) = tracker.takeState(app, time.nowMs, time.zoneId)
    private fun used(app: AppId = AppId.INSTAGRAM) = store.load(app, LocalDate.of(1970, 1, 1))

    @Test fun firstVisibleObservationDoesNotChargeUnobservedTime() {
        observe()
        assertEquals(10_000L, state().remainingMs)
        assertEquals(0L, used())
    }

    @Test fun gapOf1500MsCountsBut1501DoesNot() {
        for ((gap, expected) in listOf(1_500L to 1_500L, 1_501L to 0L)) {
            val currentStore = InMemoryAllowanceStore()
            val current = AllowanceTracker(currentStore)
            current.observe(AppId.INSTAGRAM, 0, true, 10_000, time.zoneId)
            current.observe(AppId.INSTAGRAM, gap, true, 10_000, time.zoneId)
            assertEquals(10_000 - expected, current.takeState(AppId.INSTAGRAM, gap, time.zoneId).remainingMs)
        }
    }

    @Test fun zeroGapAndDuplicateObservationDoNotDoubleCharge() {
        observe()
        observe()
        time.advanceBy(1)
        observe()
        observe()
        assertEquals(1L, used())
    }

    @Test fun nonPlayerObservationBreaksVisiblePair() {
        observe()
        time.advanceBy(1_000)
        observe(visible = false)
        time.advanceBy(100)
        observe()
        assertEquals(0L, used())
        time.advanceBy(100)
        observe()
        assertEquals(100L, used())
    }

    @Test fun explicitAppExitStopsCountingBackgroundGap() {
        observe()
        time.advanceBy(100)
        tracker.stop(AppId.INSTAGRAM, time.nowMs, time.zoneId)
        time.advanceBy(500)
        observe()
        assertEquals(0L, used())
    }

    @Test fun consumptionIsCappedAndExhaustionIsDeliveredExactlyOnce() {
        observe(limit = 1_000)
        time.advanceBy(1_500)
        observe(limit = 1_000)
        assertEquals(1_000L, used())
        assertEquals(AllowanceState(0, justExhausted = true), state())
        assertEquals(AllowanceState(), state())
        time.advanceBy(1_000)
        observe(limit = 1_000)
        assertEquals(AllowanceState(), state())
        assertEquals(1_000L, used())
    }

    @Test fun loadingAlreadyExhaustedUsageDoesNotRepeatMessage() {
        store.save(AppId.INSTAGRAM, LocalDate.of(1970, 1, 1), 1_000)
        observe(limit = 1_000)
        assertEquals(AllowanceState(), state())
    }

    @Test fun loweringLimitAppliesImmediatelyWithoutDiscardingUsedTime() {
        observe()
        repeat(3) { time.advanceBy(1_000); observe() }
        observe(limit = 2_000)
        assertEquals(AllowanceState(0, justExhausted = true), state())
        assertEquals(3_000L, used())
        observe(limit = 5_000)
        assertEquals(AllowanceState(2_000), state())
        assertEquals(3_000L, used())
    }

    @Test fun raisingLimitCanProduceOneNewExhaustionTransition() {
        observe(limit = 500)
        time.advanceBy(500)
        observe(limit = 500)
        assertTrue(state().justExhausted)
        observe(limit = 1_000)
        assertEquals(500L, state().remainingMs)
        time.advanceBy(500)
        observe(limit = 1_000)
        assertTrue(state().justExhausted)
        assertFalse(state().justExhausted)
    }

    @Test fun offPreservesUsageAndDoesNotCountOffTime() {
        observe(limit = 5_000)
        time.advanceBy(1_000)
        observe(limit = 5_000)
        observe(limit = null)
        time.advanceBy(5_000)
        observe(limit = null)
        assertEquals(AllowanceState(), state())
        observe(limit = 5_000)
        assertEquals(4_000L, state().remainingMs)
        time.advanceBy(1_000)
        observe(limit = 5_000)
        assertEquals(2_000L, used())
    }

    @Test fun usageIsSavedContinuously() {
        observe()
        time.advanceBy(500)
        observe()
        time.advanceBy(500)
        observe()
        assertEquals(2, store.saveCount)
        assertEquals(1_000L, used())
    }

    @Test fun storeRoundTripRestoresUsageWithoutCountingRestartGap() {
        observe()
        repeat(2) { time.advanceBy(1_000); observe() }
        val restarted = AllowanceTracker(store)
        time.advanceBy(500)
        restarted.observe(AppId.INSTAGRAM, time.nowMs, true, 10_000, time.zoneId)
        assertEquals(AllowanceState(8_000), restarted.takeState(AppId.INSTAGRAM, time.nowMs, time.zoneId))
    }

    @Test fun appsHaveIndependentUsageAndExhaustionEvents() {
        observe(limit = 500, app = AppId.INSTAGRAM)
        observe(limit = 1_000, app = AppId.YOUTUBE)
        time.advanceBy(500)
        observe(limit = 500, app = AppId.INSTAGRAM)
        observe(limit = 1_000, app = AppId.YOUTUBE)
        assertTrue(state(AppId.INSTAGRAM).justExhausted)
        assertEquals(AllowanceState(500), state(AppId.YOUTUBE))
    }

    @Test fun qualifyingGapIsSplitAcrossRiyadhMidnight() {
        val localTime = FakeTime(Instant.parse("2026-11-15T20:59:59.500Z").toEpochMilli(), ZoneId.of("Asia/Riyadh"))
        tracker.observe(AppId.INSTAGRAM, localTime.nowMs, true, 10_000, localTime.zoneId)
        localTime.advanceBy(1_000)
        tracker.observe(AppId.INSTAGRAM, localTime.nowMs, true, 10_000, localTime.zoneId)
        assertEquals(500L, store.load(AppId.INSTAGRAM, LocalDate.of(2026, 11, 15)))
        assertEquals(500L, store.load(AppId.INSTAGRAM, LocalDate.of(2026, 11, 16)))
        assertEquals(AllowanceState(9_500), tracker.takeState(AppId.INSTAGRAM, localTime.nowMs, localTime.zoneId))
    }

    @Test fun midnightResetDoesNotDeliverYesterdayExhaustionEvent() {
        val localTime = FakeTime(Instant.parse("2026-11-15T23:59:59Z").toEpochMilli())
        tracker.observe(AppId.INSTAGRAM, localTime.nowMs, true, 500, localTime.zoneId)
        localTime.advanceBy(1_000)
        tracker.observe(AppId.INSTAGRAM, localTime.nowMs, true, 500, localTime.zoneId)
        assertEquals(500L, store.load(AppId.INSTAGRAM, LocalDate.of(2026, 11, 15)))
        assertEquals(AllowanceState(500), tracker.takeState(AppId.INSTAGRAM, localTime.nowMs, localTime.zoneId))
    }

    @Test fun freshDateReadsZeroUsedEvenWithoutAnotherPlayerObservation() {
        observe()
        time.advanceBy(1_000)
        observe()
        time.advanceBy(86_400_000)
        assertEquals(AllowanceState(10_000), state())
    }

    @Test fun dateKeysUseSuppliedZoneRatherThanUtc() {
        val localTime = FakeTime(Instant.parse("2026-11-15T22:00:00Z").toEpochMilli(), ZoneId.of("Asia/Riyadh"))
        tracker.observe(AppId.INSTAGRAM, localTime.nowMs, true, 10_000, localTime.zoneId)
        localTime.advanceBy(1_000)
        tracker.observe(AppId.INSTAGRAM, localTime.nowMs, true, 10_000, localTime.zoneId)
        assertEquals(0L, store.load(AppId.INSTAGRAM, LocalDate.of(2026, 11, 15)))
        assertEquals(1_000L, store.load(AppId.INSTAGRAM, LocalDate.of(2026, 11, 16)))
    }

    @Test fun daylightSavingJumpCountsElapsedMilliseconds() {
        val localTime = FakeTime(Instant.parse("2026-03-08T06:59:59.500Z").toEpochMilli(), ZoneId.of("America/New_York"))
        tracker.observe(AppId.INSTAGRAM, localTime.nowMs, true, 10_000, localTime.zoneId)
        localTime.advanceBy(1_000)
        tracker.observe(AppId.INSTAGRAM, localTime.nowMs, true, 10_000, localTime.zoneId)
        assertEquals(AllowanceState(9_000), tracker.takeState(AppId.INSTAGRAM, localTime.nowMs, localTime.zoneId))
    }
}
