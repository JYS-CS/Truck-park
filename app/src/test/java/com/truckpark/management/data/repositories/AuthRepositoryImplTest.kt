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
import com.truckpark.management.domain.models.SignInResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val NOW: Instant = Instant.parse("2026-10-30T00:00:00Z")
private val USER = AuthUser(
    id = "u1",
    fullName = "Ada",
    email = "a@x.com",
    status = "APPROVED",
    roles = listOf("ADMIN"),
    permissions = setOf("user.invite"),
)

private class MemoryStore(var session: AuthSession? = null) : SessionStore {
    val verified = mutableSetOf<String>()
    var cachedUser: AuthUser? = null
    private var offlineValidUntil: Instant? = null
    var pendingCode: StoredPendingCode? = null
    var pendingSignUp: StoredPendingSignUp? = null
    override fun load() = session
    override fun save(session: AuthSession) { this.session = session }
    override fun saveCachedUser(user: AuthUser?, offlineValidUntil: Instant?) {
        cachedUser = user
        if (offlineValidUntil != null) {
            this.offlineValidUntil = offlineValidUntil
        }
    }
    override fun loadCachedUser(): AuthUser? = cachedUser
    override fun getOfflineValidUntil(): Instant? = offlineValidUntil
    override fun clear() {
        session = null
        cachedUser = null
        offlineValidUntil = null
    }
    override fun markEmailVerified(email: String) { verified += email.trim().lowercase() }
    override fun unmarkEmailVerified(email: String) { verified -= email.trim().lowercase() }
    override fun isEmailVerified(email: String): Boolean = email.trim().lowercase() in verified
    override fun savePendingCode(pending: StoredPendingCode?) { pendingCode = pending }
    override fun loadPendingCode(): StoredPendingCode? = pendingCode
    override fun savePendingSignUp(pending: StoredPendingSignUp?) { pendingSignUp = pending }
    override fun loadPendingSignUp(): StoredPendingSignUp? = pendingSignUp
}

private class FixedDevice : DeviceIdProvider {
    override fun deviceId() = "device-1"
}

private class FakeRemote : AuthRemoteDataSource {
    var refreshCalls = 0
    var meCalls = 0
    var signOutCalls = 0
    var changePasswordCalls = 0
    var registerCalls = 0
    var sendVerificationCodeCalls = 0
    var requestSignUpCodeCalls = 0
    var lastSignInEmail: String? = null
    var lastChangedEmail: String? = null
    var lastSentVerificationEmail: String? = null
    var lastRequestedSignUpEmail: String? = null
    var expectedServerCode = "654321"
    var refreshResult: AuthResult<AuthSession> = AuthResult.Success(
        AuthSession("s2", "r2", NOW.plusSeconds(3600)),
    )
    var meBehaviour: (String) -> AuthResult<AuthUser> = { AuthResult.Success(USER) }
    var checkEmailBehaviour: (String) -> AuthResult<Unit> = { AuthResult.Success(Unit) }

    override suspend fun checkEmailAvailable(email: String): AuthResult<Unit> = checkEmailBehaviour(email)

    override suspend fun requestSignUpCode(email: String, fullName: String): AuthResult<Unit> {
        val check = checkEmailBehaviour(email)
        if (check is AuthResult.Failure) return check
        requestSignUpCodeCalls++
        lastRequestedSignUpEmail = email
        return AuthResult.Success(Unit)
    }

    override suspend fun signIn(email: String, password: String, deviceId: String, deviceName: String):
        AuthResult<SignInResult> {
        lastSignInEmail = email
        return AuthResult.Success(
            SignInResult(AuthSession("s1", "r1", NOW.plusSeconds(3600)), USER, NOW.plusSeconds(259200), "sig"),
        )
    }

    override suspend fun refresh(refreshToken: String, deviceId: String): AuthResult<AuthSession> {
        refreshCalls++
        delay(20) // widen the window so concurrent callers really overlap
        return refreshResult
    }

    override suspend fun signOut(sessionToken: String): AuthResult<Unit> {
        signOutCalls++
        return AuthResult.Success(Unit)
    }

    override suspend fun me(sessionToken: String): AuthResult<AuthUser> {
        meCalls++
        return meBehaviour(sessionToken)
    }

    override suspend fun acceptInvite(token: String, fullName: String, password: String) =
        AuthResult.Success("u1")

    override suspend fun verifyEmail(userId: String, code: String, sessionToken: String?): AuthResult<Unit> {
        if (code != expectedServerCode) {
            return AuthResult.Failure(AuthError("INVALID_CODE", "Invalid or expired confirmation code.", 400))
        }
        return AuthResult.Success(Unit)
    }

    override suspend fun sendVerificationCode(sessionToken: String): AuthResult<Unit> =
        sendVerificationCode(sessionToken, null)

    override suspend fun sendVerificationCode(sessionToken: String, email: String?): AuthResult<Unit> {
        if (email != null) {
            val check = checkEmailBehaviour(email)
            if (check is AuthResult.Failure) return check
        }
        sendVerificationCodeCalls++
        lastSentVerificationEmail = email
        return AuthResult.Success(Unit)
    }

    override suspend fun register(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        code: String,
    ): AuthResult<String> {
        if (code != expectedServerCode) {
            return AuthResult.Failure(AuthError("INVALID_CODE", "Invalid or expired confirmation code.", 400))
        }
        registerCalls++
        return AuthResult.Success("u-new")
    }

    override suspend fun changePassword(
        sessionToken: String,
        newEmail: String,
        newPassword: String,
    ): AuthResult<Unit> = changePassword(sessionToken, newEmail, newPassword, expectedServerCode, null, null)

    override suspend fun changePassword(
        sessionToken: String,
        newEmail: String,
        newPassword: String,
        code: String?,
        fullName: String?,
        phone: String?,
    ): AuthResult<Unit> {
        if (code != expectedServerCode) {
            return AuthResult.Failure(AuthError("INVALID_CODE", "Invalid or expired confirmation code.", 400))
        }
        changePasswordCalls++
        lastChangedEmail = newEmail
        return AuthResult.Success(Unit)
    }
}

class AuthRepositoryImplTest {

    private val remote = FakeRemote()
    private val store = MemoryStore()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)

    private fun repo() = AuthRepositoryImpl(
        remote = remote,
        store = store,
        devices = FixedDevice(),
        deviceName = "Test phone",
        clock = clock,
    )

    private fun signedInStore(expiresIn: Long = 3600, refresh: String = "r1", token: String = "s1") {
        store.session = AuthSession(token, refresh, NOW.plusSeconds(expiresIn))
    }

    @Test
    fun signIn_normalisesEmailAndStoresSession() = runBlocking {
        repo().signIn("  Ada@X.com ", "pw")

        assertEquals("ada@x.com", remote.lastSignInEmail)
        assertEquals("s1", store.session?.sessionToken)
    }

    @Test
    fun currentUser_withoutSession_isNotSignedIn() = runBlocking {
        val failure = repo().currentUser() as AuthResult.Failure
        assertEquals(AuthError.NOT_SIGNED_IN, failure.error.code)
        assertEquals(0, remote.meCalls)
    }

    @Test
    fun currentUser_validToken_callsServerOnce_noRefresh() = runBlocking {
        signedInStore()
        val result = repo().currentUser()

        assertTrue(result is AuthResult.Success)
        assertEquals(0, remote.refreshCalls)
    }

    @Test
    fun currentUser_nearExpiry_refreshesFirstAndUsesNewToken() = runBlocking {
        signedInStore(expiresIn = 10)
        var tokenSeen: String? = null
        remote.meBehaviour = { tokenSeen = it; AuthResult.Success(USER) }

        repo().currentUser()

        assertEquals(1, remote.refreshCalls)
        assertEquals("s2", tokenSeen)
        assertEquals("r2", store.session?.refreshToken)
    }

    @Test
    fun currentUser_staleToken401_refreshesOnceAndRetries() = runBlocking {
        signedInStore()
        remote.meBehaviour = { token ->
            if (token == "s1") AuthResult.Failure(AuthError(AuthError.UNAUTHORIZED, "stale", 401))
            else AuthResult.Success(USER)
        }

        val result = repo().currentUser()

        assertTrue(result is AuthResult.Success)
        assertEquals(1, remote.refreshCalls)
        assertEquals(2, remote.meCalls)
    }

    @Test
    fun refreshRejected_deviceRevoked_clearsStoredSession() = runBlocking {
        signedInStore(expiresIn = 5)
        remote.refreshResult = AuthResult.Failure(AuthError(AuthError.DEVICE_REVOKED, "revoked", 401))

        val result = repo().currentUser()

        assertTrue(result is AuthResult.Failure)
        assertNull(store.session)
    }

    @Test
    fun refreshFailsOnNetwork_keepsSessionForLater() = runBlocking {
        signedInStore(expiresIn = 5)
        remote.refreshResult = AuthResult.Failure(AuthError(AuthError.NETWORK, "offline"))

        repo().currentUser()

        assertNotNull(store.session)
    }

    @Test
    fun concurrentCallers_shareOneRefresh() = runBlocking {
        signedInStore(expiresIn = 5)
        val repository = repo()

        List(5) { async { repository.currentUser() } }.awaitAll()

        // A second refresh with the rotated-away token would get the device revoked.
        assertEquals(1, remote.refreshCalls)
    }

    @Test
    fun signOut_clearsLocallyAndNotifiesServer() = runBlocking {
        signedInStore()
        repo().signOut()

        assertFalse(repo().hasStoredSession())
        assertEquals(1, remote.signOutCalls)
    }

    @Test
    fun signOut_withoutSession_doesNotCallServer() = runBlocking {
        repo().signOut()
        assertEquals(0, remote.signOutCalls)
    }

    @Test
    fun adminSendAndVerifyCode_dispatchesOnServerAndOnlyUpdatesDbAfterVerification() = runBlocking {
        signedInStore()
        val repository = repo()

        val sendRes = repository.sendVerificationCode("admin.new@hibiscuss.com")
        assertTrue(sendRes is AuthResult.Success)
        assertEquals(1, remote.sendVerificationCodeCalls)
        assertEquals("admin.new@hibiscuss.com", remote.lastSentVerificationEmail)
        // DB must not be updated before code is verified
        assertEquals(0, remote.changePasswordCalls)

        // Wrong code is rejected by server and DB is still untouched
        val wrong = repository.verifyAndChangeAdminCredentials(
            userId = "u1",
            newEmail = "admin.new@hibiscuss.com",
            newPassword = "NewPassword1!",
            code = "000000",
        )
        assertTrue(wrong is AuthResult.Failure)
        assertEquals(0, remote.changePasswordCalls)

        // Valid server code is accepted, updates DB, and marks email verified
        val right = repository.verifyAndChangeAdminCredentials(
            userId = "u1",
            newEmail = "admin.new@hibiscuss.com",
            newPassword = "NewPassword1!",
            code = remote.expectedServerCode,
        )
        assertTrue(right is AuthResult.Success)
        assertEquals(1, remote.changePasswordCalls)
        assertEquals("admin.new@hibiscuss.com", remote.lastChangedEmail)
        assertTrue(store.isEmailVerified("admin.new@hibiscuss.com"))
    }

    @Test
    fun signUpFlow_requestsServerCodeAndOnlyRegistersInDbAfterVerification() = runBlocking {
        val repository = repo()

        val reqRes = repository.requestSignUpVerification(
            fullName = "John Runner",
            email = "John.Runner@Example.com",
            phone = "+27821234567",
            password = "Password123!",
        )
        assertTrue(reqRes is AuthResult.Success)
        assertEquals(1, remote.requestSignUpCodeCalls)
        assertEquals("john.runner@example.com", remote.lastRequestedSignUpEmail)
        // DB must not be touched before verification
        assertEquals(0, remote.registerCalls)

        // Resend requests a fresh server code without writing to DB
        val resendRes = repository.resendSignUpVerificationCode("John.Runner@Example.com")
        assertTrue(resendRes is AuthResult.Success)
        assertEquals(2, remote.requestSignUpCodeCalls)
        assertEquals(0, remote.registerCalls)

        // Wrong code fails on server and still does not write to DB
        val wrongVerify = repository.verifySignUpEmail("john.runner@example.com", "000000")
        assertTrue(wrongVerify is AuthResult.Failure)
        assertEquals(0, remote.registerCalls)

        // Correct server code succeeds and now persists user to DB
        val rightVerify = repository.verifySignUpEmail("john.runner@example.com", remote.expectedServerCode)
        assertTrue(rightVerify is AuthResult.Success)
        assertEquals(1, remote.registerCalls)
    }

    @Test
    fun signUpFlow_survivesRepositoryRecreationWhenCheckingEmailApp() = runBlocking {
        val initialRepo = repo()
        initialRepo.requestSignUpVerification(
            fullName = "John Runner",
            email = "John.Runner@Example.com",
            phone = "+27821234567",
            password = "Password123!",
        )

        // Simulate process/repository recreation after returning from Email app
        val recreatedRepo = repo()
        val verifyRes = recreatedRepo.verifySignUpEmail("john.runner@example.com", remote.expectedServerCode)
        assertTrue(verifyRes is AuthResult.Success)
        assertEquals(1, remote.registerCalls)
    }

    @Test
    fun requestSignUpVerification_whenEmailAlreadyExistsInDb_failsBeforeSendingEmail() = runBlocking {
        remote.checkEmailBehaviour = {
            AuthResult.Failure(AuthError("ALREADY_EXISTS", "That record already exists", 409))
        }
        val repository = repo()

        val result = repository.requestSignUpVerification(
            fullName = "John Runner",
            email = "taken@example.com",
            phone = "+27821234567",
            password = "Password123!",
        )

        assertTrue(result is AuthResult.Failure)
        assertEquals("An account with this email already exists.", (result as AuthResult.Failure).error.message)
        assertEquals(0, remote.requestSignUpCodeCalls)
    }

    @Test
    fun offlineAfterSignIn_preservesCachedUserAndSessionAcrossRepoRecreation() = runBlocking {
        val initialRepo = repo()
        val signInRes = initialRepo.signIn("ada@x.com", "pw")
        assertTrue(signInRes is AuthResult.Success)
        assertEquals(USER, initialRepo.getCachedUser())

        // Simulate cold restart / repository recreation while offline (me() & refresh() fail with NETWORK)
        val recreatedRepo = repo()
        remote.meBehaviour = { AuthResult.Failure(AuthError(AuthError.NETWORK, "offline")) }
        remote.refreshResult = AuthResult.Failure(AuthError(AuthError.NETWORK, "offline"))

        val meResult = recreatedRepo.currentUser()
        assertTrue(meResult is AuthResult.Failure)
        assertEquals(AuthError.NETWORK, (meResult as AuthResult.Failure).error.code)
        assertTrue(recreatedRepo.hasStoredSession())
        assertEquals(USER, recreatedRepo.getCachedUser())
    }

    @Test
    fun deviceRevokedOnMe_clearsStoredSessionAndCachedUser() = runBlocking {
        val repository = repo()
        repository.signIn("ada@x.com", "pw")
        remote.meBehaviour = {
            AuthResult.Failure(AuthError(AuthError.DEVICE_REVOKED, "revoked", 403))
        }

        val meResult = repository.currentUser()
        assertTrue(meResult is AuthResult.Failure)
        assertFalse(repository.hasStoredSession())
        assertNull(repository.getCachedUser())
    }
}
