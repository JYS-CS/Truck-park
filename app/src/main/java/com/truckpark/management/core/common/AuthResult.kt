package com.truckpark.management.core.common

/** Failure the UI can show. [code] is stable and machine-readable; [message] is safe for people. */
data class AuthError(
    val code: String,
    val message: String,
    val httpStatus: Int? = null,
    val requestId: String? = null,
) {
    /** True when the stored sign-in can no longer be trusted and must be dropped. */
    val endsSession: Boolean
        get() = code in SESSION_ENDING_CODES

    companion object {
        const val NETWORK = "NETWORK_ERROR"
        const val BAD_RESPONSE = "BAD_RESPONSE"
        const val NOT_SIGNED_IN = "NOT_SIGNED_IN"
        const val UNAUTHORIZED = "UNAUTHORIZED"
        const val INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN"
        const val DEVICE_REVOKED = "DEVICE_REVOKED"

        private val SESSION_ENDING_CODES = setOf(INVALID_REFRESH_TOKEN, DEVICE_REVOKED)
    }
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

inline fun <T, R> AuthResult<T>.map(transform: (T) -> R): AuthResult<R> = when (this) {
    is AuthResult.Success -> AuthResult.Success(transform(value))
    is AuthResult.Failure -> this
}
