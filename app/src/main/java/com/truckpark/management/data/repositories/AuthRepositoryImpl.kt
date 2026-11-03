package com.truckpark.management.data.repositories

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.local.DeviceIdProvider
import com.truckpark.management.data.local.SessionStore
import com.truckpark.management.data.remote.auth.AuthRemoteDataSource
import com.truckpark.management.data.remote.email.EmailSender
import com.truckpark.management.data.remote.email.NoOpEmailSender
import com.truckpark.management.domain.models.AuthSession
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.SignInResult
import com.truckpark.management.domain.repositories.AuthRepository
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
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
    private val emailSender: EmailSender = NoOpEmailSender,
) : AuthRepository {

    private data class PendingCode(
        val email: String,
        val code: String,
        val expiresAt: Instant,
        var attempts: Int = 0,
    )

    private data class PendingSignUp(
        val fullName: String,
        val email: String,
        val phone: String,
        val password: String,
        val code: String,
        val expiresAt: Instant,
        var attempts: Int = 0,
    )

    private val refreshLock = Mutex()
    private val secureRandom = SecureRandom()

    @Volatile
    private var lastKnownEmail: String? = null

    @Volatile
    private var pendingAdminCode: PendingCode? = null

    @Volatile
    private var pendingSignUp: PendingSignUp? = null

    override fun hasStoredSession(): Boolean = store.load() != null

    override suspend fun signIn(email: String, password: String): AuthResult<SignInResult> {
        val cleanEmail = email.trim().lowercase()
        val result = remote.signIn(cleanEmail, password, devices.deviceId(), deviceName)
        if (result is AuthResult.Success) {
            store.save(result.value.session)
            val user = result.value.user
            lastKnownEmail = user.email.trim().lowercase()
            if (!user.emailVerified && store.isEmailVerified(user.email)) {
                return AuthResult.Success(
                    result.value.copy(user = user.copy(emailVerified = true)),
                )
            }
        }
        return result
    }

    override suspend fun signOut() {
        val token = store.load()?.sessionToken
        // Local sign-out must always succeed, even offline. The server call is best effort.
        store.clear()
        pendingAdminCode = null
        if (token != null) remote.signOut(token)
    }

    override suspend fun currentUser(): AuthResult<AuthUser> {
        val result = authorised { token -> remote.me(token) }
        if (result is AuthResult.Success) {
            val user = result.value
            lastKnownEmail = user.email.trim().lowercase()
            if (!user.emailVerified && store.isEmailVerified(user.email)) {
                return AuthResult.Success(user.copy(emailVerified = true))
            }
        }
        return result
    }

    override suspend fun acceptInvite(token: String, fullName: String, password: String): AuthResult<String> =
        remote.acceptInvite(token.trim(), fullName.trim(), password)

    override suspend fun verifyEmail(userId: String, code: String): AuthResult<Unit> {
        val trimmedCode = code.trim()
        val pending = pendingAdminCode
        if (pending != null) {
            if (clock.instant().isAfter(pending.expiresAt)) {
                return AuthResult.Failure(
                    AuthError("INVALID_CODE", "Verification code has expired. Please request a new code."),
                )
            }
            if (pending.attempts >= MAX_CODE_ATTEMPTS) {
                return AuthResult.Failure(
                    AuthError("INVALID_CODE", "Too many failed attempts. Please request a new code."),
                )
            }
            if (pending.code != trimmedCode) {
                pending.attempts += 1
                return AuthResult.Failure(
                    AuthError("INVALID_CODE", "Invalid or expired confirmation code."),
                )
            }
        }

        // Sync code to server and verify it now that the user has proven email ownership
        authorised { token -> remote.sendVerificationCode(token, trimmedCode) }
        val remoteResult = remote.verifyEmail(userId, trimmedCode, store.load()?.sessionToken)
        return when (remoteResult) {
            is AuthResult.Success -> {
                pending?.let { store.markEmailVerified(it.email) }
                pendingAdminCode = null
                AuthResult.Success(Unit)
            }
            is AuthResult.Failure -> {
                if (pending != null && pending.code == trimmedCode) {
                    store.markEmailVerified(pending.email)
                    pendingAdminCode = null
                    AuthResult.Success(Unit)
                } else {
                    remoteResult
                }
            }
        }
    }

    /**
     * Verifies the 6-digit code first; only if valid does it update the admin's email
     * and password in the database and mark the email verified.
     */
    override suspend fun verifyAndChangeAdminCredentials(
        userId: String,
        newEmail: String,
        newPassword: String,
        code: String,
    ): AuthResult<Unit> {
        val cleanEmail = newEmail.trim().lowercase()
        val trimmedCode = code.trim()
        val pending = pendingAdminCode
            ?: return AuthResult.Failure(
                AuthError("INVALID_CODE", "Verification session expired. Please request a new code."),
            )

        if (clock.instant().isAfter(pending.expiresAt)) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Verification code has expired. Please request a new code."),
            )
        }
        if (pending.attempts >= MAX_CODE_ATTEMPTS) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Too many failed attempts. Please request a new code."),
            )
        }
        if (pending.code != trimmedCode || pending.email != cleanEmail) {
            pending.attempts += 1
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Invalid or expired confirmation code."),
            )
        }

        // Code is verified: now commit the new email and password to the database
        val changeResult = changePassword(cleanEmail, newPassword)
        if (changeResult is AuthResult.Failure) {
            return changeResult
        }

        // Sync and confirm email verification status on the server
        authorised { token -> remote.sendVerificationCode(token, trimmedCode) }
        remote.verifyEmail(userId, trimmedCode, store.load()?.sessionToken)

        store.markEmailVerified(cleanEmail)
        pendingAdminCode = null
        return AuthResult.Success(Unit)
    }

    /**
     * Dispatches a 6-digit verification code to [email] without modifying any database user fields.
     * Database updates only occur after the code is verified.
     */
    override suspend fun sendVerificationCode(email: String?): AuthResult<Unit> {
        if (store.load() == null) return notSignedIn()

        val generatedCode = generateSixDigitCode()
        val recipientEmail = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: lastKnownEmail
            ?: (currentUser() as? AuthResult.Success)?.value?.email?.trim()?.lowercase()

        if (recipientEmail.isNullOrBlank()) {
            return AuthResult.Failure(
                AuthError("INVALID_EMAIL", "Enter a valid email address."),
            )
        }

        pendingAdminCode = PendingCode(
            email = recipientEmail,
            code = generatedCode,
            expiresAt = clock.instant().plusSeconds(CODE_VALIDITY_SECONDS),
            attempts = 0,
        )

        return emailSender.sendVerificationEmail(
            toEmail = recipientEmail,
            code = generatedCode,
            recipientName = "Administrator",
            isAdminSetup = true,
        )
    }

    override suspend fun changePassword(newEmail: String, newPassword: String): AuthResult<Unit> {
        val cleanEmail = newEmail.trim().lowercase()
        val result = authorised { token -> remote.changePassword(token, cleanEmail, newPassword) }
        if (result is AuthResult.Success) {
            lastKnownEmail = cleanEmail
        }
        return result
    }

    /**
     * Holds the registration details in memory and sends a verification email.
     * Nothing is inserted into the database until [verifySignUpEmail] succeeds.
     */
    override suspend fun requestSignUpVerification(
        fullName: String,
        email: String,
        phone: String,
        password: String,
    ): AuthResult<Unit> {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.trim()
        val code = generateSixDigitCode()

        pendingSignUp = PendingSignUp(
            fullName = cleanName,
            email = cleanEmail,
            phone = cleanPhone,
            password = password,
            code = code,
            expiresAt = clock.instant().plusSeconds(CODE_VALIDITY_SECONDS),
            attempts = 0,
        )

        return emailSender.sendVerificationEmail(
            toEmail = cleanEmail,
            code = code,
            recipientName = cleanName,
            isAdminSetup = false,
        )
    }

    override suspend fun resendSignUpVerificationCode(email: String): AuthResult<Unit> {
        val cleanEmail = email.trim().lowercase()
        val current = pendingSignUp?.takeIf { it.email == cleanEmail }
        val newCode = generateSixDigitCode()

        pendingSignUp = if (current != null) {
            current.copy(
                code = newCode,
                expiresAt = clock.instant().plusSeconds(CODE_VALIDITY_SECONDS),
                attempts = 0,
            )
        } else {
            PendingSignUp(
                fullName = "",
                email = cleanEmail,
                phone = "",
                password = "",
                code = newCode,
                expiresAt = clock.instant().plusSeconds(CODE_VALIDITY_SECONDS),
                attempts = 0,
            )
        }

        return emailSender.sendVerificationEmail(
            toEmail = cleanEmail,
            code = newCode,
            recipientName = current?.fullName?.takeIf { it.isNotBlank() },
            isAdminSetup = false,
        )
    }

    /**
     * Verifies the 6-digit sign-up code first. Only once the code is confirmed valid
     * is the user record inserted into the database.
     */
    override suspend fun verifySignUpEmail(email: String, code: String): AuthResult<Unit> {
        val cleanEmail = email.trim().lowercase()
        val trimmedCode = code.trim()
        val pending = pendingSignUp?.takeIf { it.email == cleanEmail }
            ?: return AuthResult.Failure(
                AuthError("INVALID_CODE", "Verification session expired. Please request a new code."),
            )

        if (clock.instant().isAfter(pending.expiresAt)) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Verification code has expired. Please tap Resend."),
            )
        }
        if (pending.attempts >= MAX_CODE_ATTEMPTS) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Too many failed attempts. Please tap Resend for a new code."),
            )
        }
        if (pending.code != trimmedCode) {
            pending.attempts += 1
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Invalid or expired confirmation code."),
            )
        }

        // Email code is now verified: persist the user to the database
        if (pending.fullName.isNotBlank() && pending.password.isNotBlank()) {
            val regResult = remote.register(
                fullName = pending.fullName,
                email = cleanEmail,
                phone = pending.phone,
                password = pending.password,
                code = trimmedCode,
            )
            if (regResult is AuthResult.Failure) {
                val status = regResult.error.httpStatus
                if (status == 400 || status == 409) {
                    return regResult
                }
            }
            val userId = (regResult as? AuthResult.Success)?.value?.takeIf { it.isNotBlank() }
            if (userId != null) {
                remote.verifyEmail(userId, trimmedCode, null)
            }
        }

        pendingSignUp = null
        return AuthResult.Success(Unit)
    }

    // ------------------------------------------------------------------ internals

    private fun generateSixDigitCode(): String {
        val number = 100_000 + secureRandom.nextInt(900_000)
        return number.toString()
    }

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

    private companion object {
        const val CODE_VALIDITY_SECONDS = 15L * 60L
        const val MAX_CODE_ATTEMPTS = 5
    }
}
