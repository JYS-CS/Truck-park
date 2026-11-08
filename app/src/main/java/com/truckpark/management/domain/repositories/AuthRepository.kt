package com.truckpark.management.domain.repositories

import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.ManagedDevice
import com.truckpark.management.domain.models.ManagedUser
import com.truckpark.management.domain.models.SignInResult

/**
 * Everything the UI needs from identity and access. Implementations decide where it
 * lives (ORDS today). Features depend on this interface only.
 */
interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthResult<SignInResult>
    suspend fun signOut()
    suspend fun currentUser(): AuthResult<AuthUser>
    suspend fun acceptInvite(token: String, fullName: String, password: String): AuthResult<String>
    suspend fun verifyEmail(userId: String, code: String): AuthResult<Unit>
    suspend fun verifyAndChangeAdminCredentials(
        userId: String,
        newEmail: String,
        newPassword: String,
        code: String,
        fullName: String? = null,
        phone: String? = null,
    ): AuthResult<Unit>
    suspend fun sendVerificationCode(email: String? = null): AuthResult<Unit>
    suspend fun changePassword(newEmail: String, newPassword: String): AuthResult<Unit>
    suspend fun requestSignUpVerification(
        fullName: String,
        email: String,
        phone: String,
        password: String,
    ): AuthResult<Unit>
    suspend fun resendSignUpVerificationCode(email: String): AuthResult<Unit>
    suspend fun verifySignUpEmail(email: String, code: String): AuthResult<Unit>
    suspend fun requestPasswordReset(email: String): AuthResult<Unit>
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult<Unit>
    suspend fun listUsers(status: String? = null): AuthResult<List<ManagedUser>>
    suspend fun updateUserRole(userId: String, roleCode: String): AuthResult<Unit>
    suspend fun listDevices(): AuthResult<List<ManagedDevice>>
    suspend fun revokeDevice(deviceId: String, reason: String): AuthResult<Unit>
    suspend fun reinstateDevice(deviceId: String): AuthResult<Unit>
    suspend fun listUserExceptions(userId: String): AuthResult<List<com.truckpark.management.domain.models.UserPermissionException>>
    suspend fun addUserException(
        userId: String,
        permissionCode: String,
        grantOrRemove: String,
        reason: String,
        expiresInHours: Int? = null,
    ): AuthResult<Unit>
    suspend fun revokeUserException(exceptionId: String): AuthResult<Unit>
    /**
     * Runs [call] with a valid session token, refreshing the token first when it is about to
     * expire and retrying once if the server says it already has. Used by other features' repositories.
     */
    suspend fun <T> authorised(call: suspend (sessionToken: String) -> AuthResult<T>): AuthResult<T>
    fun hasStoredSession(): Boolean
    fun getSessionRemainingMillis(): Long? = null
}
