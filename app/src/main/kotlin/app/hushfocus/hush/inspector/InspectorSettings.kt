package app.hushfocus.hush.inspector

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.inspectorDataStore: DataStore<Preferences> by preferencesDataStore(name = "inspector")

/** The Inspector on/off switch, persisted with DataStore. Off by default. */
class InspectorSettings(context: Context) {
    private val dataStore = context.applicationContext.inspectorDataStore

    val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("inspector_enabled")
    }
}
