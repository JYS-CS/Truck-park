package com.truckpark.management.domain.repositories

import com.truckpark.management.core.common.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val themeMode: Flow<ThemeMode>
    fun getInitialThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode)
    fun applySystemNightMode()
}
