package com.truckpark.management.domain.repositories

import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.domain.models.AuthUser
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
    suspend fun sendVerificationCode(): AuthResult<String?>
    suspend fun changePassword(newEmail: String, newPassword: String): AuthResult<Unit>
    fun hasStoredSession(): Boolean
}
