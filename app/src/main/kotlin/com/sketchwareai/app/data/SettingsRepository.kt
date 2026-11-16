package com.sketchwareai.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sketchwareai.core.model.Rules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Prefix used to suggest a package name for new projects, Sketchware-style. */
    val packagePrefix: String = DEFAULT_PACKAGE_PREFIX,
) {
    companion object { const val DEFAULT_PACKAGE_PREFIX = "com.my" }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                themeMode = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
                packagePrefix = prefs[PACKAGE_PREFIX] ?: AppSettings.DEFAULT_PACKAGE_PREFIX,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[THEME] = mode.name } }

    suspend fun setDynamicColor(enabled: Boolean) { dataStore.edit { it[DYNAMIC_COLOR] = enabled } }

    /** Returns an error message instead of saving an invalid prefix. */
    suspend fun setPackagePrefix(prefix: String): String? {
        val trimmed = prefix.trim()
        // A prefix must itself be a valid package once a final segment is appended.
        Rules.packageNameError("$trimmed.app")?.let { return it }
        dataStore.edit { it[PACKAGE_PREFIX] = trimmed }
        return null
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PACKAGE_PREFIX = stringPreferencesKey("package_prefix")
    }
}
