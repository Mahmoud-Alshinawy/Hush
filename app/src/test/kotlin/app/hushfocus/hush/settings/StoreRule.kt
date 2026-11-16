package app.hushfocus.hush.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Real Preferences DataStores on temp files. Each [open] uses a fresh scope, so a
 * "reopen" after [close] reads the file from disk like a restarted process would.
 */
class StoreRule(private val folder: TemporaryFolder) : ExternalResource() {
    private val scopes = mutableListOf<CoroutineScope>()

    fun file(name: String): File = File(folder.root, "$name.preferences_pb")

    fun open(name: String): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        scopes += scope
        return HushStores.open(file(name), scope)
    }

    /** Closes every store opened so far, so the same files can be opened again. */
    fun closeAll() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    override fun after() = closeAll()
}
