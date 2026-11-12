package com.truckpark.management.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.truckpark.management.core.common.AppConfig
import com.truckpark.management.data.local.KeystoreSessionStore
import com.truckpark.management.data.local.PreferencesDeviceIdProvider
import com.truckpark.management.data.local.db.TruckParkDatabase
import com.truckpark.management.data.local.ThemePreferencesDataSource
import com.truckpark.management.data.remote.HttpUrlConnectionTransport
import com.truckpark.management.data.remote.auth.AuthRemoteDataSource
import com.truckpark.management.data.remote.auth.OrdsAuthRemoteDataSource
import com.truckpark.management.data.remote.visits.OrdsVisitsRemoteDataSource
import com.truckpark.management.data.repositories.AuthRepositoryImpl
import com.truckpark.management.data.repositories.PrefsLastSyncStore
import com.truckpark.management.data.repositories.ThemeRepositoryImpl
import com.truckpark.management.data.repositories.VisitsRepositoryImpl
import com.truckpark.management.domain.repositories.AuthRepository
import com.truckpark.management.domain.repositories.ThemeRepository
import com.truckpark.management.domain.repositories.VisitsRepository

/** Minimal manual dependency container. Replace with Hilt/Koin if the graph grows. */
class AppContainer(context: Context) {
    val themeRepository: ThemeRepository =
        ThemeRepositoryImpl(ThemePreferencesDataSource(context.applicationContext))

    // The one line to change when the identity backend moves (for example to Supabase).
    private val authRemote: AuthRemoteDataSource =
        OrdsAuthRemoteDataSource(AppConfig.apiBaseUrl, HttpUrlConnectionTransport())

    val authRepository: AuthRepository = AuthRepositoryImpl(
        remote = authRemote,
        store = KeystoreSessionStore(context),
        devices = PreferencesDeviceIdProvider(context),
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100),
    )

    private val database: TruckParkDatabase = TruckParkDatabase.create(context)

    val visitsRepository: VisitsRepository = VisitsRepositoryImpl(
        db = database,
        remote = OrdsVisitsRemoteDataSource(AppConfig.apiBaseUrl, HttpUrlConnectionTransport()),
        auth = authRepository,
        lastSyncStore = PrefsLastSyncStore(context),
    )
}

class TruckParkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.themeRepository.applySystemNightMode()
    }
}
