package com.truckpark.management.data.repositories

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.local.DeviceIdProvider
import com.truckpark.management.data.local.SessionStore
import com.truckpark.management.data.remote.auth.AuthRemoteDataSource
import com.truckpark.management.domain.models.AuthSession
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.SignInResult
import com.truckpark.management.domain.repositories.AuthRepository
import java.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the sign-in lifecycle: stores tokens, refreshes them before they expire, and
 * retries once when the server says the access token is stale.
 *
 * Refresh is serialised with a mutex on purpose. The server treats a reused refresh
 * token as theft and revokes the whole device, so two screens must never refresh at once.
 */
class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val store: SessionStore,
    private val devices: DeviceIdProvider,
    private val deviceName: String,
    private val clock: Clock = Clock.systemUTC(),
    private val refreshLeewaySeconds: Long = 30,
) : AuthRepository {

    private val refreshLock = Mutex()

    override fun hasStoredSession(): Boolean = store.load() != null

    override suspend fun signIn(email: String, password: String): AuthResult<SignInResult> {
        val result = remote.signIn(email.trim().lowercase(), password, devices.deviceId(), deviceName)
        if (result is AuthResult.Success) store.save(result.value.session)
        return result
    }

    override suspend fun signOut() {
        val token = store.load()?.sessionToken
        // Local sign-out must always succeed, even offline. The server call is best effort.
        store.clear()
        if (token != null) remote.signOut(token)
    }

    override suspend fun currentUser(): AuthResult<AuthUser> = authorised { token -> remote.me(token) }

    override suspend fun acceptInvite(token: String, fullName: String, password: String): AuthResult<String> =
        remote.acceptInvite(token.trim(), fullName.trim(), password)

    override suspend fun verifyEmail(userId: String, code: String): AuthResult<Unit> =
        remote.verifyEmail(userId, code.trim(), store.load()?.sessionToken)

    override suspend fun sendVerificationCode(): AuthResult<String?> =
        authorised { token -> remote.sendVerificationCode(token) }

    override suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult<Unit> =
        authorised { token -> remote.changePassword(token, currentPassword, newPassword) }

    // ------------------------------------------------------------------ internals

    private suspend fun <T> authorised(call: suspend (sessionToken: String) -> AuthResult<T>): AuthResult<T> {
        var session = store.load() ?: return notSignedIn()

        if (isNearExpiry(session)) {
            when (val refreshed = refresh(session)) {
                is AuthResult.Success -> session = refreshed.value
                is AuthResult.Failure -> return refreshed
            }
        }

        val first = call(session.sessionToken)
        if (first is AuthResult.Failure && first.error.code == AuthError.UNAUTHORIZED) {
            return when (val refreshed = refresh(session)) {
                is AuthResult.Success -> call(refreshed.value.sessionToken)
                is AuthResult.Failure -> refreshed
            }
        }
        return first
    }

    private suspend fun refresh(stale: AuthSession): AuthResult<AuthSession> = refreshLock.withLock {
        val current = store.load() ?: return notSignedIn()
        // Another caller already rotated the tokens while we waited for the lock.
        if (current.refreshToken != stale.refreshToken) return AuthResult.Success(current)

        val result = remote.refresh(current.refreshToken, devices.deviceId())
        when (result) {
            is AuthResult.Success -> store.save(result.value)
            is AuthResult.Failure -> if (result.error.endsSession) store.clear()
        }
        result
    }

    private fun isNearExpiry(session: AuthSession): Boolean =
        !session.expiresAt.isAfter(clock.instant().plusSeconds(refreshLeewaySeconds))

    private fun notSignedIn() = AuthResult.Failure(
        AuthError(AuthError.NOT_SIGNED_IN, "Please sign in to continue."),
    )
}
