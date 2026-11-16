package app.hushfocus.hush.core

import java.time.LocalDate

/** App-layer adapters supply storage; values are milliseconds used, never author identities. */
interface AllowanceStore {
    fun load(app: AppId, date: LocalDate): Long
    fun save(app: AppId, date: LocalDate, usedMs: Long)
}
