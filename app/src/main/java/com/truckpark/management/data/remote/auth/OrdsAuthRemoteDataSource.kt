package com.truckpark.management.data.remote.auth

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.remote.HttpRequest
import com.truckpark.management.data.remote.HttpResponse
import com.truckpark.management.data.remote.HttpTransport
import com.truckpark.management.domain.models.AuthSession
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.SignInResult
import java.io.IOException
import java.time.Instant
import org.json.JSONException
import org.json.JSONObject

/**
 * Oracle ORDS implementation of [AuthRemoteDataSource]. Endpoints and JSON shapes match
 * backend/ords/02_ords_module.sql. [baseUrl] looks like https://host/ords/truckpark/v1/
 */
class OrdsAuthRemoteDataSource(
    baseUrl: String,
    private val transport: HttpTransport,
) : AuthRemoteDataSource {

    private val base = baseUrl.trimEnd('/') + "/"

    override suspend fun signIn(
        email: String,
        password: String,
        deviceId: String,
        deviceName: String,
    ): AuthResult<SignInResult> = call(
        path = "auth/login",
        body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("device_id", deviceId)
            .put("device_name", deviceName),
    ) { json ->
        SignInResult(
            session = AuthSession(
                sessionToken = json.getString("session_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAt = Instant.parse(json.getString("expires_at")),
            ),
            user = parseUser(json.getJSONObject("user")),
            offlineValidUntil = Instant.parse(json.getString("offline_valid_until")),
            offlineSignature = json.getString("offline_signature"),
        )
    }

    override suspend fun refresh(refreshToken: String, deviceId: String): AuthResult<AuthSession> = call(
        path = "auth/refresh",
        body = JSONObject().put("refresh_token", refreshToken).put("device_id", deviceId),
    ) { json ->
        AuthSession(
            sessionToken = json.getString("session_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = Instant.parse(json.getString("expires_at")),
        )
    }

    override suspend fun signOut(sessionToken: String): AuthResult<Unit> = call(
        path = "auth/logout",
        body = JSONObject(),
        bearer = sessionToken,
    ) { }

    override suspend fun me(sessionToken: String): AuthResult<AuthUser> = call(
        method = "GET",
        path = "me",
        bearer = sessionToken,
    ) { json -> parseUser(json) }

    override suspend fun acceptInvite(token: String, fullName: String, password: String): AuthResult<String> = call(
        path = "auth/accept-invite",
        body = JSONObject().put("token", token).put("full_name", fullName).put("password", password),
    ) { json -> json.getString("user_id") }

    override suspend fun verifyEmail(userId: String, code: String, sessionToken: String?): AuthResult<Unit> = call(
        path = "auth/verify-email",
        body = JSONObject().put("user_id", userId).put("code", code),
        bearer = sessionToken,
    ) { }

    override suspend fun sendVerificationCode(sessionToken: String): AuthResult<String?> = call(
        path = "auth/send-verification-code",
        body = JSONObject(),
        bearer = sessionToken,
    ) { json -> json.optString("code").ifBlank { null } }

    override suspend fun changePassword(
        sessionToken: String,
        newEmail: String,
        newPassword: String,
    ): AuthResult<Unit> = call(
        path = "auth/change-password",
        body = JSONObject()
            .put("new_email", newEmail)
            .put("new_password", newPassword),
        bearer = sessionToken,
    ) { }

    // ------------------------------------------------------------------ plumbing

    private suspend fun <T> call(
        method: String = "POST",
        path: String,
        body: JSONObject? = null,
        bearer: String? = null,
        parse: (JSONObject) -> T,
    ): AuthResult<T> {
        val headers = if (bearer != null) mapOf("Authorization" to "Bearer $bearer") else emptyMap()
        val response = try {
            transport.execute(HttpRequest(method, base + path, headers, body?.toString()))
        } catch (e: IOException) {
            return AuthResult.Failure(
                AuthError(AuthError.NETWORK, "Cannot reach the server. Check your connection and try again."),
            )
        }

        if (response.status !in 200..299) return AuthResult.Failure(errorFrom(response))

        return try {
            val json = if (response.body.isBlank()) JSONObject() else JSONObject(response.body)
            AuthResult.Success(parse(json))
        } catch (e: JSONException) {
            badResponse(response)
        } catch (e: java.time.format.DateTimeParseException) {
            badResponse(response)
        }
    }

    private fun badResponse(response: HttpResponse): AuthResult.Failure = AuthResult.Failure(
        AuthError(AuthError.BAD_RESPONSE, "The server sent something unexpected.", response.status),
    )

    /** Reads the common error shape: { code, message, request_id }. Never trusts the body to be JSON. */
    private fun errorFrom(response: HttpResponse): AuthError {
        val json = try { JSONObject(response.body) } catch (e: JSONException) { null }
        return AuthError(
            code = json?.optString("code").orEmpty().ifBlank { "HTTP_${response.status}" },
            message = json?.optString("message").orEmpty().ifBlank { "Request failed (${response.status})." },
            httpStatus = response.status,
            requestId = json?.optString("request_id")?.ifBlank { null },
        )
    }

    private fun parseUser(json: JSONObject): AuthUser {
        val roles = json.optJSONArray("roles")
        val permissions = json.optJSONArray("permissions")
        return AuthUser(
            id = json.getString("id"),
            fullName = json.getString("full_name"),
            email = json.getString("email"),
            status = json.getString("status"),
            emailVerified = json.optBoolean("email_verified", false),
            roles = List(roles?.length() ?: 0) { roles!!.getString(it) },
            permissions = List(permissions?.length() ?: 0) { permissions!!.getString(it) }.toSet(),
            mustChangePassword = json.optBoolean("must_change_password", false),
        )
    }
}
