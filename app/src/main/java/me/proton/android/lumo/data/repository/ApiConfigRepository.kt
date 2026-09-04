package me.proton.android.lumo.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.proton.android.lumo.llm.model.ApiConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's LLM API configuration (URL, key, provider type, custom HTTP template, etc.)
 * Uses DataStore (Preferences) so we don't need to bump Room schema when adding fields.
 */
@Singleton
class ApiConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val apiConfig: Flow<ApiConfig> = dataStore.data.map { prefs ->
        prefs[KEY_CONFIG]?.let {
            runCatching { json.decodeFromString<ApiConfig>(it) }.getOrNull()
        } ?: ApiConfig()
    }

    suspend fun current(): ApiConfig = apiConfig.first()

    suspend fun save(config: ApiConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_CONFIG] = json.encodeToString(ApiConfig.serializer(), config)
        }
    }

    companion object {
        private val KEY_CONFIG = stringPreferencesKey("llm::api_config::v1")
    }
}
