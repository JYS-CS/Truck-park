package com.truckpark.management.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.truckpark.management.core.common.AppConfig
import com.truckpark.management.data.local.KeystoreSessionStore
import com.truckpark.management.data.local.PreferencesDeviceIdProvider
import com.truckpark.management.data.local.ThemePreferencesDataSource
import com.truckpark.management.data.remote.HttpUrlConnectionTransport
import com.truckpark.management.data.remote.auth.AuthRemoteDataSource
import com.truckpark.management.data.remote.auth.OrdsAuthRemoteDataSource
import com.truckpark.management.data.remote.email.BrevoSmtpEmailSender
import com.truckpark.management.data.remote.email.EmailSender
import com.truckpark.management.data.repositories.AuthRepositoryImpl
import com.truckpark.management.data.repositories.ThemeRepositoryImpl
import com.truckpark.management.domain.repositories.AuthRepository
import com.truckpark.management.domain.repositories.ThemeRepository

/** Minimal manual dependency container. Replace with Hilt/Koin if the graph grows. */
class AppContainer(context: Context) {
    val themeRepository: ThemeRepository =
        ThemeRepositoryImpl(ThemePreferencesDataSource(context.applicationContext))

    // The one line to change when the identity backend moves (for example to Supabase).
    private val authRemote: AuthRemoteDataSource =
        OrdsAuthRemoteDataSource(AppConfig.apiBaseUrl, HttpUrlConnectionTransport())

    private val emailSender: EmailSender = BrevoSmtpEmailSender(
        smtpHost = AppConfig.BREVO_SMTP_HOST,
        smtpPort = AppConfig.BREVO_SMTP_PORT,
        smtpLogin = AppConfig.BREVO_SMTP_LOGIN,
        smtpKey = AppConfig.BREVO_SMTP_KEY,
        senderEmail = AppConfig.BREVO_SENDER_EMAIL,
    )

    val authRepository: AuthRepository = AuthRepositoryImpl(
        remote = authRemote,
        store = KeystoreSessionStore(context),
        devices = PreferencesDeviceIdProvider(context),
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100),
        emailSender = emailSender,
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
