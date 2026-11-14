package com.truckpark.management.data.remote.auth

import com.truckpark.management.core.common.AuthError
import com.truckpark.management.core.common.AuthResult
import com.truckpark.management.data.remote.HttpRequest
import com.truckpark.management.data.remote.HttpResponse
import com.truckpark.management.data.remote.HttpTransport
import com.truckpark.management.domain.models.AuthSession
import com.truckpark.management.domain.models.AuthUser
import com.truckpark.management.domain.models.ManagedDevice
import com.truckpark.management.domain.models.ManagedUser
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

    override suspend fun acceptInvite(token: String, fullName: String, password: String): AuthResult<String> =
        acceptInvite(token, fullName, password, null)

    override suspend fun acceptInvite(
        token: String,
        fullName: String,
        password: String,
        deviceId: String?,
    ): AuthResult<String> = call(
        path = "auth/accept-invite",
        body = JSONObject()
            .put("token", token)
            .put("full_name", fullName)
            .put("password", password)
            .apply { if (!deviceId.isNullOrBlank()) put("device_id", deviceId) },
    ) { json -> json.getString("user_id") }

    override suspend fun verifyEmail(userId: String, code: String, sessionToken: String?): AuthResult<Unit> = call(
        path = "auth/verify-email",
        body = JSONObject().put("user_id", userId).put("code", code),
        bearer = sessionToken,
    ) { }

    override suspend fun sendVerificationCode(sessionToken: String): AuthResult<Unit> =
        sendVerificationCode(sessionToken, null)

    override suspend fun sendVerificationCode(sessionToken: String, email: String?): AuthResult<Unit> = call(
        path = "auth/send-verification-code",
        body = JSONObject().apply {
            if (!email.isNullOrBlank()) put("email", email.trim().lowercase())
        },
        bearer = sessionToken,
    ) { }

    override suspend fun checkEmailAvailable(email: String): AuthResult<Unit> =
        checkEmailAvailable(email, null)

    override suspend fun checkEmailAvailable(email: String, deviceId: String?): AuthResult<Unit> {
        val result = call(
            path = "auth/check-email",
            body = JSONObject()
                .put("email", email.trim().lowercase())
                .apply { if (!deviceId.isNullOrBlank()) put("device_id", deviceId) },
        ) { }
        if (result is AuthResult.Failure && result.error.httpStatus == 404) {
            return AuthResult.Success(Unit)
        }
        return result
    }

    override suspend fun requestSignUpCode(email: String, fullName: String): AuthResult<Unit> =
        requestSignUpCode(email, fullName, null)

    override suspend fun requestSignUpCode(email: String, fullName: String, deviceId: String?): AuthResult<Unit> = call(
        path = "auth/request-signup-code",
        body = JSONObject()
            .put("email", email.trim().lowercase())
            .put("full_name", fullName.trim())
            .apply { if (!deviceId.isNullOrBlank()) put("device_id", deviceId) },
    ) { }

    override suspend fun register(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        code: String,
    ): AuthResult<String> = register(fullName, email, phone, password, code, null)

    override suspend fun register(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        code: String,
        deviceId: String?,
    ): AuthResult<String> = call(
        path = "auth/register",
        body = JSONObject()
            .put("full_name", fullName)
            .put("email", email)
            .put("phone", phone)
            .put("password", password)
            .put("code", code)
            .apply { if (!deviceId.isNullOrBlank()) put("device_id", deviceId) },
    ) { json -> json.optString("user_id", "") }

    override suspend fun changePassword(
        sessionToken: String,
        newEmail: String,
        newPassword: String,
    ): AuthResult<Unit> = changePassword(sessionToken, newEmail, newPassword, null)

    override suspend fun changePassword(
        sessionToken: String,
        newEmail: String,
        newPassword: String,
        code: String?,
        fullName: String?,
        phone: String?,
        currentPassword: String?,
    ): AuthResult<Unit> = call(
        path = "auth/change-password",
        body = JSONObject()
            .put("new_email", newEmail)
            .put("new_password", newPassword)
            .apply {
                if (!code.isNullOrBlank()) put("code", code.trim())
                if (!fullName.isNullOrBlank()) put("full_name", fullName.trim())
                if (!phone.isNullOrBlank()) put("phone", phone.trim())
                if (!currentPassword.isNullOrBlank()) put("current_password", currentPassword)
            },
        bearer = sessionToken,
    ) { }

    override suspend fun requestPasswordReset(email: String): AuthResult<Unit> =
        requestPasswordReset(email, null)

    override suspend fun requestPasswordReset(email: String, deviceId: String?): AuthResult<Unit> = call(
        path = "auth/request-password-reset",
        body = JSONObject()
            .put("email", email.trim().lowercase())
            .apply { if (!deviceId.isNullOrBlank()) put("device_id", deviceId) },
    ) { }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): AuthResult<Unit> = resetPassword(email, code, newPassword, null)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        deviceId: String?,
    ): AuthResult<Unit> = call(
        path = "auth/reset-password",
        body = JSONObject()
            .put("email", email.trim().lowercase())
            .put("code", code.trim())
            .put("new_password", newPassword)
            .apply { if (!deviceId.isNullOrBlank()) put("device_id", deviceId) },
    ) { }

    override suspend fun listUsers(sessionToken: String, status: String?): AuthResult<List<ManagedUser>> {
        val query = if (!status.isNullOrBlank()) "?status=${status.trim().uppercase()}" else ""
        return call(
            method = "GET",
            path = "admin/users$query",
            bearer = sessionToken,
        ) { json ->
            val items = json.optJSONArray("items")
            List(items?.length() ?: 0) { i ->
                val obj = items!!.getJSONObject(i)
                ManagedUser(
                    id = obj.getString("id"),
                    fullName = obj.optString("full_name", "Staff"),
                    email = obj.optString("email", ""),
                    phone = obj.optString("phone", ""),
                    status = obj.optString("status", "APPROVED"),
                    role = obj.optString("role", "RUNNER").ifBlank { "RUNNER" },
                    emailVerified = obj.optBoolean("email_verified", true),
                    createdAt = obj.optString("created_at", "").takeIf { it.isNotBlank() },
                    failedAttempts = obj.optInt("failed_attempts", 0),
                    lockedUntil = obj.optString("locked_until", "").takeIf { it.isNotBlank() },
                    isLocked = obj.optBoolean("is_locked", false),
                    deactivateAt = obj.optString("deactivate_at", "").takeIf { it.isNotBlank() },
                )
            }
        }
    }

    override suspend fun updateUserRole(
        sessionToken: String,
        userId: String,
        roleCode: String,
    ): AuthResult<Unit> = call(
        path = "admin/users/${userId.trim()}/approve",
        body = JSONObject().put("role_code", roleCode.trim().uppercase()),
        bearer = sessionToken,
    ) { }

    override suspend fun deactivateUser(
        sessionToken: String,
        userId: String,
    ): AuthResult<Unit> = call(
        path = "admin/users/${userId.trim()}/deactivate",
        body = JSONObject(),
        bearer = sessionToken,
    ) { }

    override suspend fun listDevices(sessionToken: String): AuthResult<List<ManagedDevice>> = call(
        method = "GET",
        path = "admin/devices",
        bearer = sessionToken,
    ) { json ->
        val items = json.optJSONArray("items")
        List(items?.length() ?: 0) { i ->
            val obj = items!!.getJSONObject(i)
            ManagedDevice(
                id = obj.getString("id"),
                userId = obj.optString("user_id", ""),
                userName = obj.optString("user_name", "Staff"),
                userEmail = obj.optString("user_email", ""),
                name = obj.optString("name", "Android Handset").takeIf { !it.equals("null", ignoreCase = true) }?.ifBlank { "Android Handset" } ?: "Android Handset",
                registeredAt = obj.optNullableString("registered_at"),
                lastSeenAt = obj.optNullableString("last_seen_at"),
                revokedAt = obj.optNullableString("revoked_at"),
                revokeReason = obj.optNullableString("revoke_reason"),
                isCurrentDevice = obj.optBoolean("is_current_device", false),
            )
        }
    }

    override suspend fun revokeDevice(
        sessionToken: String,
        deviceId: String,
        reason: String,
    ): AuthResult<Unit> = call(
        path = "admin/devices/${deviceId.trim()}/revoke",
        body = JSONObject().put("reason", reason.trim()),
        bearer = sessionToken,
    ) { }

    override suspend fun reinstateDevice(
        sessionToken: String,
        deviceId: String,
    ): AuthResult<Unit> = call(
        path = "admin/devices/${deviceId.trim()}/reinstate",
        body = JSONObject(),
        bearer = sessionToken,
    ) { }

    override suspend fun listUserExceptions(
        sessionToken: String,
        userId: String,
    ): AuthResult<List<com.truckpark.management.domain.models.UserPermissionException>> = call(
        method = "GET",
        path = "admin/users/${userId.trim()}/permissions/exceptions",
        bearer = sessionToken,
    ) { json ->
        val items = json.optJSONArray("exceptions")
        List(items?.length() ?: 0) { i ->
            val obj = items!!.getJSONObject(i)
            com.truckpark.management.domain.models.UserPermissionException(
                id = obj.getString("id"),
                userId = obj.getString("user_id"),
                permissionCode = obj.getString("permission_code"),
                grantOrRemove = obj.getString("grant_or_remove"),
                reason = obj.optString("reason", ""),
                approvedBy = obj.optString("approved_by").ifBlank { null },
                approvedAt = obj.optString("approved_at").ifBlank { null },
                expiresAt = obj.optString("expires_at").ifBlank { null },
            )
        }
    }

    override suspend fun addUserException(
        sessionToken: String,
        userId: String,
        permissionCode: String,
        grantOrRemove: String,
        reason: String,
        expiresInHours: Int?,
    ): AuthResult<Unit> = call(
        path = "admin/users/${userId.trim()}/permissions/exceptions",
        body = JSONObject().apply {
            put("permission_code", permissionCode.trim())
            put("grant_or_remove", grantOrRemove.trim().uppercase())
            put("reason", reason.trim())
            if (expiresInHours != null && expiresInHours > 0) {
                put("expires_in_hours", expiresInHours)
            }
        },
        bearer = sessionToken,
    ) { }

    override suspend fun revokeUserException(
        sessionToken: String,
        exceptionId: String,
    ): AuthResult<Unit> = call(
        path = "admin/permissions/exceptions/${exceptionId.trim()}/revoke",
        body = JSONObject(),
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
        val rawCode = json?.optString("code").orEmpty().ifBlank { "HTTP_${response.status}" }
        val rawMsg = json?.optString("message").orEmpty().ifBlank { "Request failed (${response.status})." }
        val message = if (rawCode == AuthError.DEVICE_REVOKED) {
            "Access from this device has been disabled, please contact your system administrator."
        } else {
            rawMsg
        }
        return AuthError(
            code = rawCode,
            message = message,
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
            phone = json.optString("phone").takeIf { it.isNotBlank() },
            status = json.getString("status"),
            emailVerified = json.optBoolean("email_verified", false),
            roles = List(roles?.length() ?: 0) { roles!!.getString(it) },
            permissions = List(permissions?.length() ?: 0) { permissions!!.getString(it) }.toSet(),
            mustChangePassword = json.optBoolean("must_change_password", false),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        val str = optString(key)
        return if (str.isBlank() || str.equals("null", ignoreCase = true)) null else str
    }
}
