package me.proton.android.lumo.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import me.proton.android.lumo.ui.theme.AppStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's theme choice (System / Dark / Light).
 * After the WebView removal this repository no longer injects theme into a web page —
 * it just exposes the choice to the native Compose UI.
 */
interface ThemeRepository {
    suspend fun saveTheme(theme: AppStyle)
    suspend fun getTheme(): AppStyle
    suspend fun observeTheme(isSystemInDarkMode: Boolean): Flow<AppStyle>
}

@Singleton
class ThemeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ThemeRepository {

    private val themeStream: Flow<AppStyle> = dataStore.data
        .map { prefs -> AppStyle.fromInt(prefs[KEY_THEME] ?: AppStyle.System.mode) }
        .catch { emit(AppStyle.System) }

    override suspend fun saveTheme(theme: AppStyle) {
        dataStore.edit { prefs -> prefs[KEY_THEME] = theme.mode }
    }

    override suspend fun getTheme(): AppStyle = themeStream.firstOrNull() ?: AppStyle.System

    override suspend fun observeTheme(isSystemInDarkMode: Boolean): Flow<AppStyle> = themeStream

    companion object {
        private val KEY_THEME = intPreferencesKey("key::lumo::theme")
    }
}
