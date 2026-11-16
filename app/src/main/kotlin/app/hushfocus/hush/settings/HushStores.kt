package app.hushfocus.hush.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The only way Hush's settings and allowance stores are opened: one DataStore per file
 * per process, shared by the service and the UI (DataStore fails on a second instance
 * for the same file). A corrupt file is replaced with empty data, which reads as defaults.
 */
object HushStores {
    class Stores(val settings: DataStore<Preferences>, val allowance: DataStore<Preferences>)

    private const val DIRECTORY = "datastore"
    private const val SETTINGS_FILE = "hush_settings.preferences_pb"
    private const val ALLOWANCE_FILE = "hush_allowance.preferences_pb"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var stores: Stores? = null

    fun get(context: Context): Stores = get(File(context.applicationContext.filesDir, DIRECTORY))

    /** [directory] is used only on the first call; later calls return the same stores. */
    internal fun get(directory: File): Stores =
        stores ?: synchronized(this) {
            stores ?: Stores(
                settings = open(File(directory, SETTINGS_FILE), scope),
                allowance = open(File(directory, ALLOWANCE_FILE), scope),
            ).also { stores = it }
        }

    /** Opens one Preferences file. Only HushStores and tests (with their own files) call this. */
    internal fun open(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { file },
        )
}
