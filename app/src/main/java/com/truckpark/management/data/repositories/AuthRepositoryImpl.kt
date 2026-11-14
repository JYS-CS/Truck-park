package com.truckpark.management.data.repositories

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.local.DeviceIdProvider
import com.truckpark.management.data.local.SessionStore
import com.truckpark.management.data.local.StoredPendingCode
import com.truckpark.management.data.local.StoredPendingSignUp
import com.truckpark.management.data.remote.auth.AuthRemoteDataSource
import com.truckpark.management.domain.models.AuthSession
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.ManagedDevice
import com.truckpark.management.domain.models.ManagedUser
import com.truckpark.management.domain.models.SignInResult
import com.truckpark.management.domain.repositories.AuthRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the sign-in lifecycle: stores tokens, refreshes them before they expire, and
 * retries once when the server says the access token is stale.
 *
 * Verification codes and transactional emails are generated, dispatched, and validated
 * on the backend (PKG_API), so SMTP credentials and OTP secrets never live on the client.
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

    private data class PendingCode(
        val email: String,
        val expiresAt: Instant,
        var attempts: Int = 0,
    )

    private data class PendingSignUp(
        val fullName: String,
        val email: String,
        val phone: String,
        val password: String,
        val expiresAt: Instant,
        var attempts: Int = 0,
    )

    private val refreshLock = Mutex()

    @Volatile
    private var lastKnownEmail: String? = null

    @Volatile
    private var lastKnownUser: AuthUser? = null

    @Volatile
    private var pendingAdminCode: PendingCode? = null

    @Volatile
    private var pendingSignUp: PendingSignUp? = null

    override fun hasStoredSession(): Boolean = store.load() != null

    override fun getSessionRemainingMillis(): Long? = store.getSessionRemainingMillis()

    override fun getCachedUser(): AuthUser? {
        if (store.load() == null) return null
        val offlineUntil = store.getOfflineValidUntil()
        if (offlineUntil != null && clock.instant().isAfter(offlineUntil)) return null
        return lastKnownUser ?: store.loadCachedUser()?.also { loaded ->
            lastKnownUser = loaded
            lastKnownEmail = loaded.email.trim().lowercase()
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult<SignInResult> {
        val cleanEmail = email.trim().lowercase()
        val result = remote.signIn(cleanEmail, password, devices.deviceId(), deviceName)
        if (result is AuthResult.Success) {
            store.save(result.value.session, isNewLogin = true)
            val user = result.value.user
            store.saveCachedUser(user, result.value.offlineValidUntil)
            lastKnownUser = user
            lastKnownEmail = user.email.trim().lowercase()
        }
        return result
    }

    override suspend fun signOut() {
        val token = store.load()?.sessionToken
        // Local sign-out must always succeed, even offline. The server call is best effort.
        store.clear()
        store.savePendingCode(null)
        lastKnownUser = null
        lastKnownEmail = null
        pendingAdminCode = null
        if (token != null) remote.signOut(token)
    }

    override suspend fun currentUser(): AuthResult<AuthUser> {
        val result = authorised { token -> remote.me(token) }
        if (result is AuthResult.Success) {
            val user = result.value
            store.saveCachedUser(user)
            lastKnownUser = user
            lastKnownEmail = user.email.trim().lowercase()
        }
        return result
    }

    override suspend fun acceptInvite(token: String, fullName: String, password: String): AuthResult<String> =
        remote.acceptInvite(token.trim(), fullName.trim(), password, devices.deviceId())

    override suspend fun verifyEmail(userId: String, code: String): AuthResult<Unit> {
        val trimmedCode = code.trim()
        if (!trimmedCode.matches(SIX_DIGIT_CODE_REGEX)) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Confirmation code must be 6 digits."),
            )
        }

        val pending = resolvePendingAdminCode()
        val remoteResult = remote.verifyEmail(userId, trimmedCode, store.load()?.sessionToken)
        return when (remoteResult) {
            is AuthResult.Success -> {
                pending?.let { store.markEmailVerified(it.email) }
                persistPendingAdminCode(null)
                AuthResult.Success(Unit)
            }
            is AuthResult.Failure -> remoteResult
        }
    }

    /**
     * Sends the 6-digit code and new credentials to the backend, which verifies the code first
     * and only updates the admin's email and password in the database if the code is valid.
     */
    override suspend fun verifyAndChangeAdminCredentials(
        userId: String,
        newEmail: String,
        newPassword: String,
        code: String,
        fullName: String?,
        phone: String?,
    ): AuthResult<Unit> {
        val cleanEmail = newEmail.trim().lowercase()
        val trimmedCode = code.trim()
        if (!trimmedCode.matches(SIX_DIGIT_CODE_REGEX)) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Confirmation code must be 6 digits."),
            )
        }

        val pending = resolvePendingAdminCode()
        val targetEmail = cleanEmail
            .takeIf { it.isNotBlank() && !it.equals("admin@hibiscuss.com", ignoreCase = true) }
            ?: pending?.email
            ?: return AuthResult.Failure(
                AuthError("INVALID_EMAIL", "Enter a valid email address."),
            )

        val changeResult = authorised { token ->
            remote.changePassword(
                sessionToken = token,
                newEmail = targetEmail,
                newPassword = newPassword,
                code = trimmedCode,
                fullName = fullName?.trim()?.takeIf { it.isNotEmpty() },
                phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        if (changeResult is AuthResult.Failure) {
            return changeResult
        }

        store.markEmailVerified(targetEmail)
        lastKnownEmail = targetEmail
        val updatedUser = lastKnownUser?.copy(
            email = targetEmail,
            fullName = fullName?.trim()?.takeIf { it.isNotEmpty() } ?: lastKnownUser?.fullName.orEmpty(),
            phone = phone?.trim()?.takeIf { it.isNotEmpty() } ?: lastKnownUser?.phone,
            emailVerified = true,
            mustChangePassword = false,
        )
        lastKnownUser = updatedUser
        if (updatedUser != null) store.saveCachedUser(updatedUser)
        persistPendingAdminCode(null)
        return AuthResult.Success(Unit)
    }

    /**
     * Requests the backend to generate and dispatch a 6-digit verification code to [email]
     * without modifying any user records in the database until the code is verified.
     */
    override suspend fun sendVerificationCode(email: String?): AuthResult<Unit> {
        if (store.load() == null) return notSignedIn()

        val backendUser = lastKnownUser
            ?: (currentUser() as? AuthResult.Success)?.value
        val recipientEmail = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: lastKnownEmail
            ?: backendUser?.email?.trim()?.lowercase()

        if (recipientEmail.isNullOrBlank()) {
            return AuthResult.Failure(
                AuthError("INVALID_EMAIL", "Enter a valid email address."),
            )
        }

        val result = authorised { token ->
            remote.sendVerificationCode(token, recipientEmail)
        }
        if (result is AuthResult.Failure) {
            val msg = if (result.error.httpStatus == 409 || result.error.code == "ALREADY_EXISTS") {
                "An account with this email already exists."
            } else {
                result.error.message
            }
            return AuthResult.Failure(result.error.copy(message = msg))
        }

        persistPendingAdminCode(
            PendingCode(
                email = recipientEmail,
                expiresAt = clock.instant().plusSeconds(CODE_VALIDITY_SECONDS),
                attempts = 0,
            ),
        )
        return AuthResult.Success(Unit)
    }

    override suspend fun changePassword(newEmail: String, newPassword: String): AuthResult<Unit> {
        val cleanEmail = newEmail.trim().lowercase()
        val result = authorised { token -> remote.changePassword(token, cleanEmail, newPassword) }
        if (result is AuthResult.Success) {
            lastKnownEmail = cleanEmail
            val updated = lastKnownUser?.copy(
                email = cleanEmail,
                mustChangePassword = false,
            )
            lastKnownUser = updated
            if (updated != null) store.saveCachedUser(updated)
        }
        return result
    }

    override suspend fun updateUserProfile(
        newEmail: String,
        fullName: String,
        phone: String,
        newPassword: String?,
        code: String?,
    ): AuthResult<AuthUser> {
        val current = lastKnownUser ?: getCachedUser()
            ?: (currentUser() as? AuthResult.Success)?.value
            ?: return notSignedIn()

        val cleanEmail = newEmail.trim().lowercase().ifBlank { current.email.trim().lowercase() }
        val cleanName = fullName.trim().ifBlank { current.fullName }
        val cleanPhone = phone.trim()
        val cleanPwd = newPassword?.takeIf { it.isNotBlank() }.orEmpty()
        val cleanCode = code?.trim()?.takeIf { it.isNotBlank() }

        if (cleanPwd.isNotEmpty()) {
            val isStrictValid = cleanPwd.length in 8..100 &&
                cleanPwd.any { it.isUpperCase() } &&
                cleanPwd.any { it.isLowerCase() } &&
                cleanPwd.any { it.isDigit() } &&
                cleanPwd.any { !it.isLetterOrDigit() } &&
                cleanPwd != "@Admin"
            if (!isStrictValid) {
                return AuthResult.Failure(
                    AuthError(
                        "WEAK_PASSWORD",
                        "Password needs 8+ characters with an uppercase letter, lowercase letter, number, and symbol.",
                    ),
                )
            }
        }

        val emailChanged = !cleanEmail.equals(current.email.trim(), ignoreCase = true)
        if (emailChanged && (cleanCode == null || !cleanCode.matches(SIX_DIGIT_CODE_REGEX))) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Enter the 6-digit verification code sent to your new email address."),
            )
        }

        val remoteResult = authorised { token ->
            remote.changePassword(
                sessionToken = token,
                newEmail = cleanEmail,
                newPassword = cleanPwd,
                code = cleanCode,
                fullName = cleanName,
                phone = cleanPhone,
            )
        }

        val updatedUser = current.copy(
            email = cleanEmail,
            fullName = cleanName,
            phone = cleanPhone.ifBlank { current.phone },
            emailVerified = true,
            mustChangePassword = false,
        )

        return when (remoteResult) {
            is AuthResult.Success -> {
                if (emailChanged) store.markEmailVerified(cleanEmail)
                lastKnownEmail = cleanEmail
                lastKnownUser = updatedUser
                store.saveCachedUser(updatedUser)
                persistPendingAdminCode(null)
                AuthResult.Success(updatedUser)
            }
            is AuthResult.Failure -> {
                // If only updating name/phone and backend is still running the legacy change_password check (-20003),
                // update local cached user gracefully when email & password were not changed.
                if (!emailChanged && cleanPwd.isEmpty() &&
                    remoteResult.error.message.contains("Account setup is already complete", ignoreCase = true)
                ) {
                    lastKnownUser = updatedUser
                    store.saveCachedUser(updatedUser)
                    AuthResult.Success(updatedUser)
                } else {
                    remoteResult
                }
            }
        }
    }

    /**
     * Requests the backend to check that [email] is not already registered and to send a
     * 6-digit verification code via Brevo. Nothing is inserted into `users` or `auth_identities`
     * until [verifySignUpEmail] succeeds on the backend.
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

        if (cleanEmail.equals("admin@hibiscuss.com", ignoreCase = true) || store.isEmailVerified(cleanEmail)) {
            return AuthResult.Failure(
                AuthError("ALREADY_EXISTS", "An account with this email already exists.", 409),
            )
        }

        when (val sendResult = remote.requestSignUpCode(cleanEmail, cleanName, devices.deviceId())) {
            is AuthResult.Failure -> {
                val msg = if (sendResult.error.httpStatus == 409 || sendResult.error.code == "ALREADY_EXISTS") {
                    "An account with this email already exists."
                } else {
                    sendResult.error.message
                }
                return AuthResult.Failure(sendResult.error.copy(message = msg))
            }
            is AuthResult.Success -> Unit
        }

        persistPendingSignUp(
            PendingSignUp(
                fullName = cleanName,
                email = cleanEmail,
                phone = cleanPhone,
                password = password,
                expiresAt = clock.instant().plusSeconds(CODE_VALIDITY_SECONDS),
                attempts = 0,
            ),
        )

        return AuthResult.Success(Unit)
    }

    override suspend fun resendSignUpVerificationCode(email: String): AuthResult<Unit> {
        val cleanEmail = email.trim().lowercase()
        val current = resolvePendingSignUp(cleanEmail)
        val targetEmail = cleanEmail.ifEmpty { current?.email.orEmpty() }
        if (targetEmail.isBlank()) {
            return AuthResult.Failure(
                AuthError("INVALID_EMAIL", "A valid email address is required."),
            )
        }

        when (val sendResult = remote.requestSignUpCode(targetEmail, current?.fullName.orEmpty(), devices.deviceId())) {
            is AuthResult.Failure -> return sendResult
            is AuthResult.Success -> Unit
        }

        val updated = current?.copy(
            email = targetEmail,
            expiresAt = clock.instant().plusSeconds(CODE_VALIDITY_SECONDS),
            attempts = 0,
        ) ?: PendingSignUp(
            fullName = "",
            email = targetEmail,
            phone = "",
            password = "",
            expiresAt = clock.instant().plusSeconds(CODE_VALIDITY_SECONDS),
            attempts = 0,
        )
        persistPendingSignUp(updated)
        return AuthResult.Success(Unit)
    }

    /**
     * Submits the pending sign-up details and 6-digit [code] to the backend (`POST auth/register`).
     * The backend verifies [code] first and only inserts the user into the database if valid.
     */
    override suspend fun verifySignUpEmail(email: String, code: String): AuthResult<Unit> {
        val cleanEmail = email.trim().lowercase()
        val trimmedCode = code.trim()
        if (!trimmedCode.matches(SIX_DIGIT_CODE_REGEX)) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Confirmation code must be 6 digits."),
            )
        }

        val pending = resolvePendingSignUp(cleanEmail)
            ?: return AuthResult.Failure(
                AuthError("INVALID_CODE", "Verification session expired. Please tap Change details and try again."),
            )

        if (clock.instant().isAfter(pending.expiresAt)) {
            return AuthResult.Failure(
                AuthError("INVALID_CODE", "Verification code has expired. Please tap Resend."),
            )
        }

        val targetEmail = cleanEmail.ifEmpty { pending.email }

        if (pending.fullName.isBlank() || pending.password.isBlank()) {
            return AuthResult.Failure(
                AuthError("SESSION_EXPIRED", "Registration details expired. Please tap Change details and try again."),
            )
        }

        val regResult = remote.register(
            fullName = pending.fullName,
            email = targetEmail,
            phone = pending.phone,
            password = pending.password,
            code = trimmedCode,
            deviceId = devices.deviceId(),
        )
        if (regResult is AuthResult.Failure) {
            val status = regResult.error.httpStatus
            if (status == 409 || regResult.error.code == "ALREADY_EXISTS") {
                return AuthResult.Failure(
                    regResult.error.copy(message = "An account with this email already exists."),
                )
            }
            return regResult
        }

        store.markEmailVerified(targetEmail)
        val signUpPassword = pending.password
        persistPendingSignUp(null)

        // New accounts are auto-approved as RUNNER on the backend; sign in immediately
        signIn(targetEmail, signUpPassword)
        return AuthResult.Success(Unit)
    }

    override suspend fun requestPasswordReset(email: String): AuthResult<Unit> =
        remote.requestPasswordReset(email.trim().lowercase(), devices.deviceId())

    override suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult<Unit> =
        remote.resetPassword(email.trim().lowercase(), code.trim(), newPassword, devices.deviceId())

    override suspend fun listUsers(status: String?): AuthResult<List<ManagedUser>> =
        authorised { token -> remote.listUsers(token, status) }

    override suspend fun updateUserRole(userId: String, roleCode: String): AuthResult<Unit> =
        authorised { token -> remote.updateUserRole(token, userId, roleCode) }

    override suspend fun listDevices(): AuthResult<List<ManagedDevice>> =
        authorised { token -> remote.listDevices(token) }

    override suspend fun revokeDevice(deviceId: String, reason: String): AuthResult<Unit> =
        authorised { token -> remote.revokeDevice(token, deviceId, reason) }

    override suspend fun reinstateDevice(deviceId: String): AuthResult<Unit> =
        authorised { token -> remote.reinstateDevice(token, deviceId) }

    override suspend fun listUserExceptions(userId: String): AuthResult<List<com.truckpark.management.domain.models.UserPermissionException>> =
        authorised { token -> remote.listUserExceptions(token, userId) }

    override suspend fun addUserException(
        userId: String,
        permissionCode: String,
        grantOrRemove: String,
        reason: String,
        expiresInHours: Int?,
    ): AuthResult<Unit> = authorised { token ->
        remote.addUserException(token, userId, permissionCode, grantOrRemove, reason, expiresInHours)
    }

    override suspend fun revokeUserException(exceptionId: String): AuthResult<Unit> =
        authorised { token -> remote.revokeUserException(token, exceptionId) }

    // ------------------------------------------------------------------ internals

    private fun resolvePendingAdminCode(): PendingCode? {
        pendingAdminCode?.let { return it }
        val loaded = store.loadPendingCode() ?: return null
        return PendingCode(
            email = loaded.email,
            expiresAt = loaded.expiresAt,
            attempts = loaded.attempts,
        ).also { pendingAdminCode = it }
    }

    private fun persistPendingAdminCode(pending: PendingCode?) {
        pendingAdminCode = pending
        store.savePendingCode(
            pending?.let {
                StoredPendingCode(
                    email = it.email,
                    code = "SERVER",
                    expiresAt = it.expiresAt,
                    attempts = it.attempts,
                )
            },
        )
    }

    private fun resolvePendingSignUp(cleanEmail: String): PendingSignUp? {
        val current = pendingSignUp ?: store.loadPendingSignUp()?.let {
            PendingSignUp(
                fullName = it.fullName,
                email = it.email,
                phone = it.phone,
                password = it.password,
                expiresAt = it.expiresAt,
                attempts = it.attempts,
            ).also { loaded -> pendingSignUp = loaded }
        }
        return current?.takeIf { cleanEmail.isEmpty() || it.email.equals(cleanEmail, ignoreCase = true) }
            ?: current
    }

    private fun persistPendingSignUp(pending: PendingSignUp?) {
        pendingSignUp = pending
        store.savePendingSignUp(
            pending?.let {
                StoredPendingSignUp(
                    fullName = it.fullName,
                    email = it.email,
                    phone = it.phone,
                    password = it.password,
                    code = "SERVER",
                    expiresAt = it.expiresAt,
                    attempts = it.attempts,
                )
            },
        )
    }

    override suspend fun <T> authorised(call: suspend (sessionToken: String) -> AuthResult<T>): AuthResult<T> {
        var session = store.load() ?: return notSignedIn()

        if (isNearExpiry(session)) {
            when (val refreshed = refresh(session)) {
                is AuthResult.Success -> session = refreshed.value
                is AuthResult.Failure -> return refreshed
            }
        }

        val first = call(session.sessionToken)
        if (first is AuthResult.Failure && first.error.endsSession) {
            store.clear()
            return first
        }
        if (first is AuthResult.Failure && first.error.code == AuthError.UNAUTHORIZED) {
            return when (val refreshed = refresh(session)) {
                is AuthResult.Success -> {
                    val retried = call(refreshed.value.sessionToken)
                    if (retried is AuthResult.Failure && retried.error.endsSession) {
                        store.clear()
                    }
                    retried
                }
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
            is AuthResult.Success -> store.save(result.value, isNewLogin = false)
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
        val SIX_DIGIT_CODE_REGEX = Regex("^[0-9]{6}$")
    }
}
