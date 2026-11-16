package app.hushfocus.hush.rules

import app.hushfocus.hush.core.InstagramScreen

/** Classification history only: no old nodes, targets, rectangles or actions are retained. */
class ScreenStabilizer {
    data class Result(val rawScreen: InstagramScreen, val stableScreen: InstagramScreen, val isHeld: Boolean)

    private var previous = InstagramScreen.UNKNOWN
    private var unknownSinceMs: Long? = null

    fun update(screen: InstagramScreen, nowMs: Long): Result {
        if (screen != InstagramScreen.UNKNOWN) {
            previous = screen
            unknownSinceMs = null
            return Result(screen, screen, false)
        }
        val since = unknownSinceMs ?: nowMs.also { unknownSinceMs = it }
        val hold = previous != InstagramScreen.UNKNOWN && nowMs - since < InstagramRules.UNKNOWN_HOLD_MS
        return Result(screen, if (hold) previous else InstagramScreen.UNKNOWN, hold)
    }

    fun reset() { previous = InstagramScreen.UNKNOWN; unknownSinceMs = null }
}
