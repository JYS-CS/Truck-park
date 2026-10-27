package com.truckpark.management.app

import android.app.Application
import android.content.Context
import com.truckpark.management.data.local.ThemePreferencesDataSource
import com.truckpark.management.data.repositories.ThemeRepositoryImpl
import com.truckpark.management.domain.repositories.ThemeRepository

/** Minimal manual dependency container. Replace with Hilt/Koin if the graph grows. */
class AppContainer(context: Context) {
    val themeRepository: ThemeRepository =
        ThemeRepositoryImpl(ThemePreferencesDataSource(context.applicationContext))
}

class TruckParkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
