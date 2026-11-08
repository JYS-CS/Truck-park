package com.truckpark.management.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Local persistent store for known transporters / fleet carriers.
 * Ensures that once a transporter is entered, it automatically populates
 * the autocomplete dropdown for fast and effortless re-entry.
 */
object TransporterStore {
    private const val PREFS_NAME = "transporters_store"
    private const val KEY_TRANSPORTERS = "known_transporters"

    private val DEFAULT_TRANSPORTERS = setOf(
        "BHL Logistics",
        "Unitrans",
        "Alistair Group",
        "Cargo Carriers",
        "J&J Transport",
        "Impala Terminals",
        "SGC Transport",
        "Bolloré Logistics",
        "Cross Border Hauliers",
        "Stallion Transport",
        "Ndola Haulage",
        "Copperbelt Logistics",
        "Speedy Transporters",
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getKnownTransporters(context: Context): Set<String> {
        val saved = getPrefs(context).getStringSet(KEY_TRANSPORTERS, null)
        return if (saved.isNullOrEmpty()) DEFAULT_TRANSPORTERS else (DEFAULT_TRANSPORTERS + saved)
    }

    fun addTransporter(context: Context, transporter: String) {
        val clean = transporter.trim()
        if (clean.length < 2) return
        val current = getKnownTransporters(context).toMutableSet()
        if (current.add(clean)) {
            getPrefs(context).edit().putStringSet(KEY_TRANSPORTERS, current).apply()
        }
    }

    fun searchTransporters(context: Context, query: String): List<String> {
        val clean = query.trim().lowercase()
        val all = getKnownTransporters(context).toList().sorted()
        if (clean.isEmpty()) return all.take(10)
        return all.filter { it.lowercase().contains(clean) }.take(10)
    }
}

