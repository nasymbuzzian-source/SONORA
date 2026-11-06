package app.sonora.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val ctx: Context) {
    private val accentKey = intPreferencesKey("accent")
    private val restoreKey = booleanPreferencesKey("restore_queue")
    private val stateKey = stringPreferencesKey("player_state")

    val accent: Flow<Int> = ctx.dataStore.data.map { it[accentKey] ?: 0 }
    val restore: Flow<Boolean> = ctx.dataStore.data.map { it[restoreKey] ?: true }

    suspend fun setAccent(i: Int) {
        ctx.dataStore.edit { it[accentKey] = i }
    }

    suspend fun setRestore(b: Boolean) {
        ctx.dataStore.edit { it[restoreKey] = b }
    }

    suspend fun saveState(s: String) {
        ctx.dataStore.edit { it[stateKey] = s }
    }

    suspend fun loadState(): String? = ctx.dataStore.data.first()[stateKey]
}
