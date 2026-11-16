package app.hushfocus.hush.core

import java.time.LocalDate

internal class InMemoryAllowanceStore : AllowanceStore {
    val values = mutableMapOf<Pair<AppId, LocalDate>, Long>()
    var saveCount = 0
        private set

    override fun load(app: AppId, date: LocalDate): Long = values[app to date] ?: 0

    override fun save(app: AppId, date: LocalDate, usedMs: Long) {
        values[app to date] = usedMs
        saveCount++
    }
}
