package com.truckpark.management.core.common

/**
 * Global application configuration and remote endpoint references.
 */
object AppConfig {
    /**
     * Oracle Autonomous Database ORDS base host URL.
     * Stored in .env and local.properties.
     */
    const val ORDS_BASE_URL: String = "https://gf339ddde581301-hibiscusstruckpark.adb.af-johannesburg-1.oraclecloudapps.com/ords/"

    /**
     * ORDS REST module namespace (defaults to admin on the current schema).
     */
    const val ORDS_MODULE_PATH: String = "admin"

    /**
     * API version segment.
     */
    const val ORDS_API_VERSION: String = "v1"

    /**
     * Fully qualified base URL for API network requests:
     * e.g. https://gf339ddde581301-hibiscusstruckpark.adb.af-johannesburg-1.oraclecloudapps.com/ords/truckpark/v1/
     */
    val apiBaseUrl: String
        get() = "${ORDS_BASE_URL.trimEnd('/')}/$ORDS_MODULE_PATH/$ORDS_API_VERSION/"
}
