package com.truckpark.management.data.repositories

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.local.DeviceIdProvider
import com.truckpark.management.data.local.SessionStore
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
private val USER = AuthUser("u1", "Ada", "a@x.com", "APPROVED", listOf("ADMIN"), setOf("user.invite"))

private class MemoryStore(var session: AuthSession? = null) : SessionStore {
    override fun load() = session
    override fun save(session: AuthSession) { this.session = session }
    override fun clear() { session = null }
}

private class FixedDevice : DeviceIdProvider {
    override fun deviceId() = "device-1"
}

private class FakeRemote : AuthRemoteDataSource {
    var refreshCalls = 0
    var meCalls = 0
    var signOutCalls = 0
    var lastSignInEmail: String? = null
    var refreshResult: AuthResult<AuthSession> = AuthResult.Success(
        AuthSession("s2", "r2", NOW.plusSeconds(3600)),
    )
    var meBehaviour: (String) -> AuthResult<AuthUser> = { AuthResult.Success(USER) }

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

    override suspend fun verifyEmail(userId: String, code: String, sessionToken: String?) = AuthResult.Success(Unit)

    override suspend fun sendVerificationCode(sessionToken: String): AuthResult<String?> = AuthResult.Success("123456")

    override suspend fun changePassword(
        sessionToken: String,
        currentPassword: String,
        newEmail: String,
        newPassword: String,
    ): AuthResult<Unit> =
        AuthResult.Success(Unit)
}

class AuthRepositoryImplTest {

    private val remote = FakeRemote()
    private val store = MemoryStore()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)

    private fun repo() = AuthRepositoryImpl(remote, store, FixedDevice(), "Test phone", clock)

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
}
