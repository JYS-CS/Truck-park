package com.truckpark.management.data.repositories

import com.truckpark.management.core.common.ThemeMode
import com.truckpark.management.data.local.ThemePreferencesDataSource
import com.truckpark.management.domain.repositories.ThemeRepository
import kotlinx.coroutines.flow.Flow

class ThemeRepositoryImpl(
    private val dataSource: ThemePreferencesDataSource,
) : ThemeRepository {
    override val themeMode: Flow<ThemeMode> = dataSource.themeMode
    override fun getInitialThemeMode(): ThemeMode = dataSource.getInitialThemeMode()
    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)
    override fun applySystemNightMode() = dataSource.applySystemNightMode()
}
