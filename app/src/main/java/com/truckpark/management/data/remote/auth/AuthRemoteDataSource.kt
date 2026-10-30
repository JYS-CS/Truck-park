package com.truckpark.management.data.remote.auth

import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.domain.models.AuthSession
import com.truckpark.management.domain.models.AuthUser
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
    suspend fun verifyEmail(userId: String, code: String, sessionToken: String? = null): AuthResult<Unit>
    suspend fun sendVerificationCode(sessionToken: String): AuthResult<Unit>
    suspend fun changePassword(
        sessionToken: String,
        newEmail: String,
        newPassword: String,
    ): AuthResult<Unit>
}
