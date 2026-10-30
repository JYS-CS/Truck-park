package com.truckpark.management.data.remote.auth

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.remote.HttpRequest
import com.truckpark.management.data.remote.HttpResponse
import com.truckpark.management.data.remote.HttpTransport
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTransport(private val reply: (HttpRequest) -> HttpResponse) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return reply(request)
    }
}

class OrdsAuthRemoteDataSourceTest {

    private val loginJson = """
        {"session_token":"s1","expires_at":"2026-10-30T01:00:00Z","refresh_token":"r1",
         "offline_valid_until":"2026-11-02T00:00:00Z","offline_signature":"sig",
         "user":{"id":"u1","full_name":"Ada Admin","email":"a@x.com","status":"APPROVED",
                 "roles":["ADMIN"],"permissions":["user.invite","audit.view"]},
         "request_id":"abc"}
    """.trimIndent()

    private fun source(transport: HttpTransport) =
        OrdsAuthRemoteDataSource("https://host/ords/truckpark/v1", transport)

    @Test
    fun signIn_postsJsonToLoginEndpoint() = runBlocking {
        val transport = FakeTransport { HttpResponse(200, loginJson) }
        source(transport).signIn("a@x.com", "pw", "dev-1", "Pixel")

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://host/ords/truckpark/v1/auth/login", request.url)
        val body = JSONObject(request.body!!)
        assertEquals("a@x.com", body.getString("email"))
        assertEquals("dev-1", body.getString("device_id"))
    }

    @Test
    fun signIn_parsesSessionAndUser() = runBlocking {
        val result = source(FakeTransport { HttpResponse(200, loginJson) })
            .signIn("a@x.com", "pw", "dev-1", "Pixel") as AuthResult.Success

        assertEquals("s1", result.value.session.sessionToken)
        assertEquals("r1", result.value.session.refreshToken)
        assertEquals(listOf("ADMIN"), result.value.user.roles)
        assertTrue(result.value.user.can("user.invite"))
        assertEquals("sig", result.value.offlineSignature)
    }

    @Test
    fun serverError_isMappedToCodeMessageAndRequestId() = runBlocking {
        val transport = FakeTransport {
            HttpResponse(401, """{"code":"INVALID_CREDENTIALS","message":"Invalid email or password","request_id":"r9"}""")
        }
        val failure = source(transport).signIn("a@x.com", "bad", "d", "n") as AuthResult.Failure

        assertEquals("INVALID_CREDENTIALS", failure.error.code)
        assertEquals("Invalid email or password", failure.error.message)
        assertEquals(401, failure.error.httpStatus)
        assertEquals("r9", failure.error.requestId)
    }

    @Test
    fun nonJsonErrorBody_doesNotCrash() = runBlocking {
        val failure = source(FakeTransport { HttpResponse(502, "<html>Bad gateway</html>") })
            .signIn("a@x.com", "pw", "d", "n") as AuthResult.Failure

        assertEquals("HTTP_502", failure.error.code)
        assertNull(failure.error.requestId)
    }

    @Test
    fun unreachableServer_becomesNetworkError() = runBlocking {
        val failure = source(FakeTransport { throw IOException("down") })
            .signIn("a@x.com", "pw", "d", "n") as AuthResult.Failure

        assertEquals(AuthError.NETWORK, failure.error.code)
    }

    @Test
    fun malformedSuccessBody_becomesBadResponse() = runBlocking {
        val failure = source(FakeTransport { HttpResponse(200, """{"session_token":"only"}""") })
            .signIn("a@x.com", "pw", "d", "n") as AuthResult.Failure

        assertEquals(AuthError.BAD_RESPONSE, failure.error.code)
    }

    @Test
    fun me_sendsBearerHeaderAndUsesGet() = runBlocking {
        val transport = FakeTransport {
            HttpResponse(200, """{"id":"u1","full_name":"Ada","email":"a@x.com","status":"APPROVED","roles":[],"permissions":[]}""")
        }
        source(transport).me("tok123")

        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("Bearer tok123", request.headers["Authorization"])
        assertNull(request.body)
    }

    @Test
    fun baseUrlWithoutTrailingSlash_stillBuildsCleanPaths() = runBlocking {
        val transport = FakeTransport { HttpResponse(200, "{}") }
        OrdsAuthRemoteDataSource("https://host/v1", transport).verifyEmail("u1", "123456")

        assertEquals("https://host/v1/auth/verify-email", transport.requests.single().url)
    }
}
