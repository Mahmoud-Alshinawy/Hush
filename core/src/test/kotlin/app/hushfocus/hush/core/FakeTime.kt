package app.hushfocus.hush.core

import java.time.ZoneId

internal class FakeTime(startMs: Long = 0, val zoneId: ZoneId = ZoneId.of("UTC")) {
    var nowMs: Long = startMs
        private set

    fun advanceBy(ms: Long) {
        require(ms >= 0)
        nowMs += ms
    }
}
