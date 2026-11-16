package app.hushfocus.hush.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionLogTest {
    private val time = FakeTime()
    private val log = ActionLog()

    @Test fun noExecutionMeansNoCooldown() { assertNull(log.msSinceLastNavigationAction(time.nowMs)) }

    @Test fun executedBackReportsCooldownBoundaryValues() {
        log.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Back, true))
        assertEquals(0L, log.msSinceLastNavigationAction(time.nowMs))
        time.advanceBy(999)
        assertEquals(999L, log.msSinceLastNavigationAction(time.nowMs))
        time.advanceBy(1)
        assertEquals(1_000L, log.msSinceLastNavigationAction(time.nowMs))
    }

    @Test fun successfulTapAlsoStartsCooldown() {
        log.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Tap(ActionTarget.DMS_TAB), true))
        time.advanceBy(100)
        assertEquals(100L, log.msSinceLastNavigationAction(time.nowMs))
        assertNull(log.takeFailedTap())
    }

    @Test fun failedTapStartsCooldownAndIsReportedOnlyOnce() {
        log.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Tap(ActionTarget.DMS_TAB), false))
        assertEquals(0L, log.msSinceLastNavigationAction(time.nowMs))
        assertEquals(ActionTarget.DMS_TAB, log.takeFailedTap())
        assertNull(log.takeFailedTap())
    }

    @Test fun coversMessagesAndScrollsDoNotChangeNavigationCooldown() {
        log.recordExecution(ActionExecutionFeedback(time.nowMs, Action.Back, true))
        time.advanceBy(500)
        for (action in listOf(Action.HideAllCovers, Action.ShowMessage(HushMessage.NAVIGATION_FAILED),
            Action.ScrollTo(ActionTarget.FEED_ABOVE_CAUGHT_UP))) {
            log.recordExecution(ActionExecutionFeedback(time.nowMs, action, false))
        }
        assertEquals(500L, log.msSinceLastNavigationAction(time.nowMs))
        assertNull(log.takeFailedTap())
    }
}
