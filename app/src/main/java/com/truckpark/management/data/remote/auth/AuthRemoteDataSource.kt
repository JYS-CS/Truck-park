package com.truckpark.management.data.remote.auth

import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.domain.models.AuthSession
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.ManagedDevice
import com.truckpark.management.domain.models.ManagedUser
import com.truckpark.management.domain.models.SignInResult

/**
 * Identity calls against whichever backend hosts them. To move from Oracle ORDS to
 * Supabase (or anything else), add a new implementation and change one line in
 * AppContainer. Nothing above this interface changes.
 */
interface AuthRemoteDataSource {
    suspend fun signIn(email: String, password: String, deviceId: String, deviceName: String): AuthResult<SignInResult>
    suspend fun refresh(refreshToken: String, deviceId: String): AuthResult<AuthSession>
    suspend fun signOut(sessionToken: String): AuthResult<Unit>
    suspend fun me(sessionToken: String): AuthResult<AuthUser>
    suspend fun acceptInvite(token: String, fullName: String, password: String): AuthResult<String>
    suspend fun acceptInvite(
        token: String,
        fullName: String,
        password: String,
        deviceId: String?,
    ): AuthResult<String> = acceptInvite(token, fullName, password)
    suspend fun verifyEmail(userId: String, code: String, sessionToken: String? = null): AuthResult<Unit>
    suspend fun sendVerificationCode(sessionToken: String): AuthResult<Unit>
    suspend fun sendVerificationCode(sessionToken: String, email: String?): AuthResult<Unit> =
        sendVerificationCode(sessionToken)
    suspend fun checkEmailAvailable(email: String): AuthResult<Unit> = AuthResult.Success(Unit)
    suspend fun checkEmailAvailable(email: String, deviceId: String?): AuthResult<Unit> =
        checkEmailAvailable(email)
    suspend fun requestSignUpCode(email: String, fullName: String = ""): AuthResult<Unit> =
        checkEmailAvailable(email)
    suspend fun requestSignUpCode(email: String, fullName: String, deviceId: String?): AuthResult<Unit> =
        requestSignUpCode(email, fullName)
    suspend fun register(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        code: String,
    ): AuthResult<String> = AuthResult.Success("")
    suspend fun register(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        code: String,
        deviceId: String?,
    ): AuthResult<String> = register(fullName, email, phone, password, code)
    suspend fun changePassword(
        sessionToken: String,
        newEmail: String,
        newPassword: String,
    ): AuthResult<Unit>
    suspend fun changePassword(
        sessionToken: String,
        newEmail: String,
        newPassword: String,
        code: String?,
        fullName: String? = null,
        phone: String? = null,
    ): AuthResult<Unit> = changePassword(sessionToken, newEmail, newPassword)
    suspend fun requestPasswordReset(email: String): AuthResult<Unit> = AuthResult.Success(Unit)
    suspend fun requestPasswordReset(email: String, deviceId: String?): AuthResult<Unit> =
        requestPasswordReset(email)
    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): AuthResult<Unit> = AuthResult.Success(Unit)
    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        deviceId: String?,
    ): AuthResult<Unit> = resetPassword(email, code, newPassword)
    suspend fun listUsers(sessionToken: String, status: String? = null): AuthResult<List<ManagedUser>> =
        AuthResult.Success(emptyList())
    suspend fun updateUserRole(
        sessionToken: String,
        userId: String,
        roleCode: String,
    ): AuthResult<Unit> = AuthResult.Success(Unit)
    suspend fun listDevices(sessionToken: String): AuthResult<List<ManagedDevice>> =
        AuthResult.Success(emptyList())
    suspend fun revokeDevice(
        sessionToken: String,
        deviceId: String,
        reason: String,
    ): AuthResult<Unit> = AuthResult.Success(Unit)
    suspend fun reinstateDevice(
        sessionToken: String,
        deviceId: String,
    ): AuthResult<Unit> = AuthResult.Success(Unit)
    suspend fun listUserExceptions(
        sessionToken: String,
        userId: String,
    ): AuthResult<List<com.truckpark.management.domain.models.UserPermissionException>> =
        AuthResult.Success(emptyList())
    suspend fun addUserException(
        sessionToken: String,
        userId: String,
        permissionCode: String,
        grantOrRemove: String,
        reason: String,
        expiresInHours: Int? = null,
    ): AuthResult<Unit> = AuthResult.Success(Unit)
    suspend fun revokeUserException(
        sessionToken: String,
        exceptionId: String,
    ): AuthResult<Unit> = AuthResult.Success(Unit)
}
