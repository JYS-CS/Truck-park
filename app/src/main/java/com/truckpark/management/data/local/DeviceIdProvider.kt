package com.truckpark.management.data.local

import android.content.Context
import java.util.UUID

/** Stable per-install identifier the backend binds sessions to. Not a secret. */
interface DeviceIdProvider {
    fun deviceId(): String
}

class PreferencesDeviceIdProvider(context: Context) : DeviceIdProvider {
    private val prefs = context.applicationContext.getSharedPreferences("device_identity", Context.MODE_PRIVATE)

    @Synchronized
    override fun deviceId(): String {
        prefs.getString(KEY, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).commit()
        return created
    }

    private companion object {
        const val KEY = "device_id"
    }
}
