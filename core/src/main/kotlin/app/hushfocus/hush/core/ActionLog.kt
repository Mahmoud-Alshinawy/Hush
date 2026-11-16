package app.hushfocus.hush.core

class ActionLog {
    private var lastNavigationAtMs: Long? = null
    private var pendingFailedTap: ActionTarget? = null

    fun recordExecution(feedback: ActionExecutionFeedback) {
        if (feedback.action.isNavigation()) {
            lastNavigationAtMs = maxOf(lastNavigationAtMs ?: feedback.atMs, feedback.atMs)
        }
        if (feedback.action is Action.Tap && !feedback.succeeded) {
            pendingFailedTap = feedback.action.target
        }
    }

    fun msSinceLastNavigationAction(nowMs: Long): Long? =
        lastNavigationAtMs?.let { (nowMs - it).coerceAtLeast(0) }

    /** Consume once while assembling the next policy input. */
    fun takeFailedTap(): ActionTarget? = pendingFailedTap.also { pendingFailedTap = null }
}
