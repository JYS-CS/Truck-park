package com.truckpark.management.data.local

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.truckpark.management.core.common.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** Persists the appearance preference so it survives process death and restarts. */
class ThemePreferencesDataSource(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val syncPrefs = context.getSharedPreferences("theme_sync", Context.MODE_PRIVATE)

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            val mode = ThemeMode.fromStorage(prefs[themeKey])
            syncPrefs.edit().putString("theme_mode", mode.storageValue).commit()
            applySystemNightMode(mode)
            mode
        }

    fun getInitialThemeMode(): ThemeMode {
        val saved = syncPrefs.getString("theme_mode", null)
        return ThemeMode.fromStorage(saved)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        syncPrefs.edit().putString("theme_mode", mode.storageValue).commit()
        applySystemNightMode(mode)
        context.settingsDataStore.edit { it[themeKey] = mode.storageValue }
    }

    fun applySystemNightMode(mode: ThemeMode = getInitialThemeMode()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = context.getSystemService(UiModeManager::class.java)
            val target = when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            }
            uiModeManager?.setApplicationNightMode(target)
        }
    }
}
