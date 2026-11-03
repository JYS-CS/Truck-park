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
    /** True while the account still has its setup password; the app must show the setup gate. */
    val mustChangePassword: Boolean = false,
) {
    /**
     * True when the backend indicates this user is using the default admin setup credentials
     * or has not yet completed admin email/password setup.
     */
    val isAdminSetupRequired: Boolean
        get() = mustChangePassword || (roles.contains("ADMIN") && !emailVerified)

    val primaryRole: String
        get() = when {
            "ADMIN" in roles -> "ADMIN"
            "SUPERVISOR" in roles -> "SUPERVISOR"
            else -> roles.firstOrNull() ?: "RUNNER"
        }

    val isAdmin: Boolean
        get() = "ADMIN" in roles || "user.approve" in permissions || "device.manage" in permissions

    val isSupervisor: Boolean
        get() = "SUPERVISOR" in roles

    fun can(permission: String): Boolean = permission in permissions
}

/** Staff account entry returned by the admin user management endpoint. */
data class ManagedUser(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String = "",
    val status: String,
    val role: String,
)

/** Registered handset entry returned by the admin device management endpoint. */
data class ManagedDevice(
    val id: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val name: String,
    val registeredAt: String? = null,
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
    val revokeReason: String? = null,
    val isCurrentDevice: Boolean = false,
) {
    val isRevoked: Boolean
        get() = !revokedAt.isNullOrBlank()
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
