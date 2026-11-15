package app.hushfocus.hush.inspector

/**
 * Trailing debounce with a maximum wait: a burst of events fires once, [trailingMs]
 * after the latest event but never later than [maxWaitMs] after the first, so a
 * constant stream (a playing video) can't postpone a capture forever.
 */
class Debouncer(
    private val trailingMs: Long = 400,
    private val maxWaitMs: Long = 1_000,
) {
    private var burstStartMs: Long? = null

    /** Records an event at [nowMs] and returns the time the capture should fire. */
    fun onEvent(nowMs: Long): Long {
        val start = burstStartMs ?: nowMs.also { burstStartMs = it }
        return minOf(nowMs + trailingMs, start + maxWaitMs)
    }

    /** Call when the capture ran; the next event starts a new burst. */
    fun onFired() {
        burstStartMs = null
    }
}
