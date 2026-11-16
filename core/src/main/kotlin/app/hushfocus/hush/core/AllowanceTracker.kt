package app.hushfocus.hush.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AllowanceTracker(private val store: AllowanceStore) {
    private class AppState(
        var date: LocalDate,
        var usedMs: Long,
        var limitMs: Long?,
        var zoneId: ZoneId,
    ) {
        var previousVisibleAtMs: Long? = null
        var exhaustionPending = false
        fun remainingMs(): Long = limitMs?.let { (it - usedMs).coerceAtLeast(0) } ?: 0
    }

    private val states = mutableMapOf<AppId, AppState>()

    fun observe(app: AppId, atMs: Long, playerVisible: Boolean, limitMs: Long?, zoneId: ZoneId) {
        require(limitMs == null || limitMs >= 0)
        val current = states.getOrPut(app) {
            val date = dateAt(atMs, zoneId)
            AppState(date, load(app, date), limitMs, zoneId)
        }
        val previousAt = current.previousVisibleAtMs
        if (playerVisible && previousAt != null && current.zoneId == zoneId &&
            atMs - previousAt in 1L..1_500L
        ) chargeInterval(app, current, previousAt, atMs, zoneId)

        selectDate(app, current, dateAt(atMs, zoneId))
        val remainingBeforeLimitChange = current.remainingMs()
        current.limitMs = limitMs
        current.zoneId = zoneId
        if (limitMs != null && remainingBeforeLimitChange > 0 && current.remainingMs() == 0L) {
            current.exhaustionPending = true
        }
        if (limitMs == null || current.remainingMs() > 0) current.exhaustionPending = false
        current.previousVisibleAtMs = if (playerVisible && limitMs != null) atMs else null
    }

    /** A foreground/player exit breaks the pair of visible observations without charging a gap. */
    fun stop(app: AppId, atMs: Long, zoneId: ZoneId) {
        val current = states[app] ?: return
        observe(app, atMs, playerVisible = false, current.limitMs, zoneId)
    }

    /** Consume the transition event once; a date change also refreshes settings-screen reads. */
    fun takeState(app: AppId, nowMs: Long, zoneId: ZoneId): AllowanceState {
        val current = states[app] ?: return AllowanceState()
        selectDate(app, current, dateAt(nowMs, zoneId))
        val result = AllowanceState(current.remainingMs(),
            current.exhaustionPending && current.limitMs != null && current.remainingMs() == 0L)
        current.exhaustionPending = false
        return result
    }

    private fun chargeInterval(app: AppId, current: AppState, startMs: Long, endMs: Long, zoneId: ZoneId) {
        val limit = current.limitMs ?: return
        var cursor = startMs
        while (cursor < endMs) {
            val date = dateAt(cursor, zoneId)
            selectDate(app, current, date)
            val midnight = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val partEnd = minOf(endMs, midnight)
            val added = minOf(partEnd - cursor, (limit - current.usedMs).coerceAtLeast(0))
            if (added > 0) {
                val before = current.remainingMs()
                current.usedMs += added
                store.save(app, date, current.usedMs)
                if (before > 0 && current.remainingMs() == 0L) current.exhaustionPending = true
            }
            cursor = partEnd
        }
    }

    private fun selectDate(app: AppId, current: AppState, date: LocalDate) {
        if (current.date == date) return
        current.date = date
        current.usedMs = load(app, date)
        current.exhaustionPending = false
    }

    private fun load(app: AppId, date: LocalDate): Long = store.load(app, date).also { require(it >= 0) }

    private fun dateAt(atMs: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(atMs).atZone(zoneId).toLocalDate()
}
