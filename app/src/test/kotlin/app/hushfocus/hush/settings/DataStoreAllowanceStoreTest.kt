package app.hushfocus.hush.settings

import androidx.datastore.preferences.core.edit
import app.hushfocus.hush.core.AppId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreAllowanceStoreTest {
    private val folder = TemporaryFolder()
    private val stores = StoreRule(folder)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(folder).around(stores)

    private val writerScope = CoroutineScope(Dispatchers.IO + Job())
    private val today = LocalDate.of(2026, 11, 16)

    @After
    fun stopWriter() = writerScope.cancel()

    private suspend fun openStore() =
        DataStoreAllowanceStore.open(stores.open("allowance"), writerScope) { today }

    @Test
    fun nothingStoredLoadsZero() = runBlocking {
        assertEquals(0L, openStore().load(AppId.INSTAGRAM, today))
    }

    @Test
    fun saveThenLoadSurvivesReopen() = runBlocking {
        val store = openStore()
        store.save(AppId.INSTAGRAM, today, 90_000)
        assertEquals(90_000L, store.load(AppId.INSTAGRAM, today))
        store.flush()

        stores.closeAll()
        assertEquals(90_000L, openStore().load(AppId.INSTAGRAM, today))
    }

    @Test
    fun appsAndDatesStaySeparate() = runBlocking {
        val yesterday = today.minusDays(1)
        val store = openStore()
        store.save(AppId.INSTAGRAM, today, 1_000)
        store.save(AppId.INSTAGRAM, yesterday, 2_000)
        store.save(AppId.YOUTUBE, today, 3_000)
        store.save(AppId.YOUTUBE, yesterday, 4_000)
        store.flush()

        stores.closeAll()
        val again = openStore()
        assertEquals(1_000L, again.load(AppId.INSTAGRAM, today))
        assertEquals(2_000L, again.load(AppId.INSTAGRAM, yesterday))
        assertEquals(3_000L, again.load(AppId.YOUTUBE, today))
        assertEquals(4_000L, again.load(AppId.YOUTUBE, yesterday))
    }

    @Test
    fun laterSaveForSameDayWins() = runBlocking {
        val store = openStore()
        repeat(50) { store.save(AppId.INSTAGRAM, today, it * 1_000L) }
        store.flush()
        stores.closeAll()
        assertEquals(49_000L, openStore().load(AppId.INSTAGRAM, today))
    }

    @Test
    fun entriesOlderThanSevenDaysArePrunedOnWrite() = runBlocking {
        val eightDaysAgo = today.minusDays(8)
        val sevenDaysAgo = today.minusDays(7)
        stores.open("allowance").edit {
            it[SettingsKeys.allowance(AppId.INSTAGRAM, eightDaysAgo)] = 8_000
            it[SettingsKeys.allowance(AppId.INSTAGRAM, sevenDaysAgo)] = 7_000
        }
        stores.closeAll()

        val store = openStore()
        store.save(AppId.INSTAGRAM, today, 1)
        store.flush()
        assertEquals(0L, store.load(AppId.INSTAGRAM, eightDaysAgo))
        assertEquals(7_000L, store.load(AppId.INSTAGRAM, sevenDaysAgo))

        stores.closeAll()
        val keys = stores.open("allowance").data.first().asMap().keys.map { it.name }
        assertFalse(SettingsKeys.allowance(AppId.INSTAGRAM, eightDaysAgo).name in keys)
        assertTrue(SettingsKeys.allowance(AppId.INSTAGRAM, sevenDaysAgo).name in keys)
    }

    @Test
    fun storesOnlyAllowanceEntries() = runBlocking {
        val store = openStore()
        store.save(AppId.INSTAGRAM, today, 5_000)
        store.flush()
        stores.closeAll()
        val keys = stores.open("allowance").data.first().asMap().keys.map { it.name }
        assertEquals(listOf("v1.allowance.INSTAGRAM.2026-11-16"), keys)
    }
}
