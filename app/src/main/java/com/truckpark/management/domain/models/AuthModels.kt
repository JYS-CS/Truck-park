package com.truckpark.management.domain.models

import java.time.Instant

/** Signed-in staff member as the server describes them. */
data class AuthUser(
    val id: String,
    val fullName: String,
    val email: String,
    val status: String,
    val phone: String? = null,
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
    val emailVerified: Boolean = true,
    val createdAt: String? = null,
    val failedAttempts: Int = 0,
    val lockedUntil: String? = null,
    val isLocked: Boolean = false,
    val deactivateAt: String? = null,
)

/** Granular permission override for an individual staff member. */
data class UserPermissionException(
    val id: String,
    val userId: String,
    val permissionCode: String,
    val grantOrRemove: String, // "GRANT" or "REMOVE"
    val reason: String,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val expiresAt: String? = null,
) {
    val isGrant: Boolean get() = grantOrRemove.equals("GRANT", ignoreCase = true)
    val isRemove: Boolean get() = grantOrRemove.equals("REMOVE", ignoreCase = true)
}

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
    val loginAt: Instant = Instant.now(),
) {
    override fun toString(): String = "AuthSession(expiresAt=$expiresAt, loginAt=$loginAt, tokens=<redacted>)"
}

/** What a successful sign-in gives back. */
data class SignInResult(
    val session: AuthSession,
    val user: AuthUser,
    val offlineValidUntil: Instant,
    val offlineSignature: String,
)
