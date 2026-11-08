package com.truckpark.management.feature.auth

data class CountryDialCode(
    val iso: String,
    val name: String,
    val dialCode: String,
    val flag: String,
)

val LIGHTWEIGHT_COUNTRY_CODES = listOf(
    CountryDialCode("ZM", "Zambia", "+260", "🇿🇲"),
)

val DEFAULT_COUNTRY_CODE = LIGHTWEIGHT_COUNTRY_CODES.first()
