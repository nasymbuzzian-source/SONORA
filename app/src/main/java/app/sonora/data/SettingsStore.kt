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

enum class KeyKind { Jamendo, SpotifyId, SpotifySecret }

class SettingsStore(private val ctx: Context) {
    private val accentKey = intPreferencesKey("accent")
    private val restoreKey = booleanPreferencesKey("restore_queue")
    private val stateKey = stringPreferencesKey("player_state")
    private val jamendoKey = stringPreferencesKey("jamendo_id")
    private val spotifyIdKey = stringPreferencesKey("spotify_id")
    private val spotifySecretKey = stringPreferencesKey("spotify_secret")

    val accent: Flow<Int> = ctx.dataStore.data.map { it[accentKey] ?: 0 }
    val restore: Flow<Boolean> = ctx.dataStore.data.map { it[restoreKey] ?: true }

    val jamendoId: Flow<String> = ctx.dataStore.data.map { it[jamendoKey] ?: "" }
    val spotifyId: Flow<String> = ctx.dataStore.data.map { it[spotifyIdKey] ?: "" }
    val spotifySecret: Flow<String> = ctx.dataStore.data.map { it[spotifySecretKey] ?: "" }

    suspend fun setKey(kind: KeyKind, value: String) {
        ctx.dataStore.edit {
            when (kind) {
                KeyKind.Jamendo -> it[jamendoKey] = value
                KeyKind.SpotifyId -> it[spotifyIdKey] = value
                KeyKind.SpotifySecret -> it[spotifySecretKey] = value
            }
        }
    }

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
