package com.truckpark.management.core.common

import com.truckpark.management.BuildConfig

/**
 * Global application configuration and remote endpoint references.
 */
object AppConfig {
    /**
     * Oracle Autonomous Database ORDS base host URL.
     * Stored in .env and local.properties.
     */
    const val ORDS_BASE_URL: String = BuildConfig.ORDS_BASE_URL

    /**
     * ORDS REST module namespace (defaults to admin on the current schema).
     */
    const val ORDS_MODULE_PATH: String = BuildConfig.ORDS_MODULE_PATH

    /**
     * API version segment.
     */
    const val ORDS_API_VERSION: String = BuildConfig.ORDS_API_VERSION

    /**
     * Fully qualified base URL for API network requests:
     * e.g. https://gf339ddde581301-hibiscusstruckpark.adb.af-johannesburg-1.oraclecloudapps.com/ords/admin/v1/
     */
    val apiBaseUrl: String
        get() = "${ORDS_BASE_URL.trimEnd('/')}/$ORDS_MODULE_PATH/$ORDS_API_VERSION/"
}
