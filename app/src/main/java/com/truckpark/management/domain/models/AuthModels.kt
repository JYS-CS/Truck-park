package com.truckpark.management.domain.models

import java.time.Instant

/** Signed-in staff member as the server describes them. */
data class AuthUser(
    val id: String,
    val fullName: String,
    val email: String,
    val status: String,
    val emailVerified: Boolean = false,
    val roles: List<String>,
    val permissions: Set<String>,
) {
    fun can(permission: String): Boolean = permission in permissions
}

/** Opaque access and refresh tokens. Never log these. */
data class AuthSession(
    val sessionToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
) {
    override fun toString(): String = "AuthSession(expiresAt=$expiresAt, tokens=<redacted>)"
}

/** What a successful sign-in gives back. */
data class SignInResult(
    val session: AuthSession,
    val user: AuthUser,
    val offlineValidUntil: Instant,
    val offlineSignature: String,
)
