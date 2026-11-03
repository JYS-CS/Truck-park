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

    @Test
    fun sendVerificationCode_includesEmailInPayloadWhenProvided() = runBlocking {
        val transport = FakeTransport { HttpResponse(200, """{"status":"SENT"}""") }
        source(transport).sendVerificationCode("tok-admin", "admin.new@hibiscuss.com")

        val request = transport.requests.single()
        assertEquals("https://host/ords/truckpark/v1/auth/send-verification-code", request.url)
        assertEquals("Bearer tok-admin", request.headers["Authorization"])
        assertEquals("admin.new@hibiscuss.com", JSONObject(request.body!!).getString("email"))
    }

    @Test
    fun requestSignUpCode_postsEmailAndNameToRequestSignUpCodeEndpoint() = runBlocking {
        val transport = FakeTransport { HttpResponse(200, """{"status":"SENT"}""") }
        source(transport).requestSignUpCode("jane@x.com", "Jane Runner")

        val request = transport.requests.single()
        assertEquals("https://host/ords/truckpark/v1/auth/request-signup-code", request.url)
        val body = JSONObject(request.body!!)
        assertEquals("jane@x.com", body.getString("email"))
        assertEquals("Jane Runner", body.getString("full_name"))
    }

    @Test
    fun register_postsSignUpPayloadToRegisterEndpoint() = runBlocking {
        val transport = FakeTransport { HttpResponse(201, """{"user_id":"u99","status":"PENDING_VERIFICATION"}""") }
        val res = source(transport).register("Jane Runner", "jane@x.com", "+27123456789", "Pass123!", "112233")
            as AuthResult.Success

        assertEquals("u99", res.value)
        val request = transport.requests.single()
        assertEquals("https://host/ords/truckpark/v1/auth/register", request.url)
        val body = JSONObject(request.body!!)
        assertEquals("Jane Runner", body.getString("full_name"))
        assertEquals("jane@x.com", body.getString("email"))
        assertEquals("112233", body.getString("code"))
    }

    @Test
    fun listDevices_parsesDeviceListAndCurrentDeviceFlag() = runBlocking {
        val devicesJson = """
            {"items":[
                {"id":"dev-1","user_id":"u1","user_name":"Ada Admin","user_email":"a@x.com",
                 "name":"Pixel 8","registered_at":"2026-10-30T00:00:00Z","last_seen_at":"2026-10-30T01:00:00Z",
                 "is_current_device":true}
            ],"request_id":"r1"}
        """.trimIndent()
        val transport = FakeTransport { HttpResponse(200, devicesJson) }
        val res = source(transport).listDevices("tok-admin") as AuthResult.Success

        assertEquals(1, res.value.size)
        val device = res.value.single()
        assertEquals("dev-1", device.id)
        assertEquals("Pixel 8", device.name)
        assertEquals("Ada Admin", device.userName)
        assertTrue(device.isCurrentDevice)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://host/ords/truckpark/v1/admin/devices", request.url)
        assertEquals("Bearer tok-admin", request.headers["Authorization"])
    }

    @Test
    fun revokeDevice_postsReasonToRevokeEndpoint() = runBlocking {
        val transport = FakeTransport { HttpResponse(200, """{"ok":true}""") }
        val res = source(transport).revokeDevice("tok-admin", "dev-99", "Lost or stolen handset")

        assertTrue(res is AuthResult.Success)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://host/ords/truckpark/v1/admin/devices/dev-99/revoke", request.url)
        assertEquals("Lost or stolen handset", JSONObject(request.body!!).getString("reason"))
    }
}
