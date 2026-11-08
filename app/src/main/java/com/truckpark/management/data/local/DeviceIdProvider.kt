package com.truckpark.management.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

/** Stable per-device identifier the backend binds sessions to. Not a secret. */
interface DeviceIdProvider {
    fun deviceId(): String
}

class PreferencesDeviceIdProvider(context: Context) : DeviceIdProvider {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("device_identity", Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    @Synchronized
    override fun deviceId(): String {
        // Hardware-persistent ANDROID_ID so device identity and revocation survive app uninstalls & reinstalls
        val androidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()

        if (!androidId.isNullOrBlank()) {
            val stableId = androidId.take(36)
            prefs.edit().putString(KEY, stableId).apply()
            return stableId
        }

        prefs.getString(KEY, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).apply()
        return created
    }

    private companion object {
        const val KEY = "device_id"
    }
}
