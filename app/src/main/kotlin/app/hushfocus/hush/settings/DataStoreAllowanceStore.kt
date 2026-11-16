package app.hushfocus.hush.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import app.hushfocus.hush.core.AllowanceStore
import app.hushfocus.hush.core.AppId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * `:core`'s AllowanceStore on DataStore: milliseconds used per app per local date, and
 * nothing else. Reads come from an in-memory copy loaded once by [open], so the
 * synchronous interface never blocks; writes go to disk in order on [scope]. Entries
 * older than 7 days are pruned on each write.
 */
class DataStoreAllowanceStore private constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    private val today: () -> LocalDate,
    initial: Map<Pair<AppId, LocalDate>, Long>,
) : AllowanceStore {

    private sealed interface Request {
        data class Write(val app: AppId, val date: LocalDate) : Request
        data class Flush(val done: CompletableDeferred<Unit>) : Request
    }

    private val cache = ConcurrentHashMap(initial)
    private val requests = Channel<Request>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (request in requests) {
                when (request) {
                    is Request.Write -> persist(request.app, request.date)
                    is Request.Flush -> request.done.complete(Unit)
                }
            }
        }
    }

    override fun load(app: AppId, date: LocalDate): Long = cache[app to date] ?: 0L

    override fun save(app: AppId, date: LocalDate, usedMs: Long) {
        require(usedMs >= 0) { "usedMs must not be negative" }
        cache[app to date] = usedMs
        requests.trySend(Request.Write(app, date))
    }

    /** Waits until every save made so far is on disk. */
    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        requests.send(Request.Flush(done))
        done.await()
    }

    private suspend fun persist(app: AppId, date: LocalDate) {
        // Write the latest cached value, so writes can never land out of order.
        val usedMs = cache[app to date] ?: return
        val cutoff = today().minusDays(KEEP_DAYS)
        try {
            dataStore.edit { p ->
                p[SettingsKeys.allowance(app, date)] = usedMs
                for (key in p.asMap().keys.toList()) {
                    val (keyApp, keyDate) = SettingsKeys.parseAllowance(key.name) ?: continue
                    if (keyDate.isBefore(cutoff)) {
                        p.remove(key)
                        cache.remove(keyApp to keyDate)
                    }
                }
            }
        } catch (_: IOException) {
            // Disk trouble: the in-memory value stays and the next save tries again.
        }
    }

    companion object {
        private const val KEEP_DAYS = 7L

        /** Loads what's stored, then returns a store ready for synchronous use. */
        suspend fun open(
            dataStore: DataStore<Preferences>,
            scope: CoroutineScope,
            today: () -> LocalDate = LocalDate::now,
        ): DataStoreAllowanceStore {
            val stored = dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .first()
            val initial = stored.asMap().mapNotNull { (key, value) ->
                val entry = SettingsKeys.parseAllowance(key.name) ?: return@mapNotNull null
                (value as? Long)?.let { entry to it }
            }.toMap()
            return DataStoreAllowanceStore(dataStore, scope, today, initial)
        }
    }
}
