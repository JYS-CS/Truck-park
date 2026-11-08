package com.truckpark.management.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.truckpark.management.domain.models.AuthSession
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class StoredPendingCode(
    val email: String,
    val code: String,
    val expiresAt: Instant,
    val attempts: Int = 0,
)

data class StoredPendingSignUp(
    val fullName: String,
    val email: String,
    val phone: String,
    val password: String,
    val code: String,
    val expiresAt: Instant,
    val attempts: Int = 0,
)

/** Where the app keeps its sign-in between launches. */
interface SessionStore {
    fun load(): AuthSession?
    fun save(session: AuthSession)
    fun save(session: AuthSession, isNewLogin: Boolean) = save(session)
    fun getSessionRemainingMillis(): Long? = null
    fun clear()
    fun markEmailVerified(email: String) {}
    fun unmarkEmailVerified(email: String) {}
    fun isEmailVerified(email: String): Boolean = false
    fun savePendingCode(pending: StoredPendingCode?) {}
    fun loadPendingCode(): StoredPendingCode? = null
    fun savePendingSignUp(pending: StoredPendingSignUp?) {}
    fun loadPendingSignUp(): StoredPendingSignUp? = null
}

/**
 * Stores tokens encrypted with an AES-256-GCM key that lives in the Android Keystore and
 * never leaves it. If anything cannot be decrypted (restore, key reset), the session is
 * dropped and the user signs in again. Failing closed is the intended behaviour.
 */
class KeystoreSessionStore(context: Context) : SessionStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun load(): AuthSession? {
        val stored = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val json = decryptJson(stored)
            val loginEpoch = json.optLong("l", 0L)
            val nowSec = Instant.now().epochSecond
            val timeoutSec = com.truckpark.management.core.common.AppConfig.SESSION_TIMEOUT_SECONDS
            if (loginEpoch > 0L && (nowSec - loginEpoch) >= timeoutSec) {
                // Session expired based on configured auto-logout duration
                clear()
                return null
            }
            AuthSession(
                sessionToken = json.getString("s"),
                refreshToken = json.getString("r"),
                expiresAt = Instant.ofEpochSecond(json.getLong("e")),
                loginAt = if (loginEpoch > 0L) Instant.ofEpochSecond(loginEpoch) else Instant.now(),
            )
        } catch (e: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    override fun save(session: AuthSession) {
        save(session, isNewLogin = false)
    }

    @Synchronized
    override fun save(session: AuthSession, isNewLogin: Boolean) {
        val loginEpoch = if (isNewLogin) {
            session.loginAt.epochSecond
        } else {
            val existing = try {
                prefs.getString(KEY_BLOB, null)?.let { decryptJson(it) }
            } catch (_: Exception) {
                null
            }
            val prev = existing?.optLong("l", 0L) ?: 0L
            if (prev > 0L) prev else session.loginAt.epochSecond
        }

        val json = JSONObject()
            .put("s", session.sessionToken)
            .put("r", session.refreshToken)
            .put("e", session.expiresAt.epochSecond)
            .put("l", loginEpoch)
        prefs.edit()
            .putString(KEY_BLOB, encryptJson(json))
            .commit()
    }

    @Synchronized
    override fun getSessionRemainingMillis(): Long? {
        val stored = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val json = decryptJson(stored)
            val loginEpoch = json.optLong("l", 0L)
            if (loginEpoch <= 0L) return null
            val nowSec = Instant.now().epochSecond
            val timeoutSec = com.truckpark.management.core.common.AppConfig.SESSION_TIMEOUT_SECONDS
            val elapsedSec = nowSec - loginEpoch
            val remainingSec = timeoutSec - elapsedSec
            if (remainingSec <= 0L) 0L else remainingSec * 1000L
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    override fun clear() {
        prefs.edit().remove(KEY_BLOB).commit()
    }

    @Synchronized
    override fun markEmailVerified(email: String) {
        val clean = email.trim().lowercase()
        if (clean.isEmpty()) return
        val current = prefs.getStringSet(KEY_VERIFIED_EMAILS, emptySet()).orEmpty().toMutableSet()
        current.add(clean)
        prefs.edit().putStringSet(KEY_VERIFIED_EMAILS, current).commit()
    }

    @Synchronized
    override fun unmarkEmailVerified(email: String) {
        val clean = email.trim().lowercase()
        if (clean.isEmpty()) return
        val current = prefs.getStringSet(KEY_VERIFIED_EMAILS, emptySet()).orEmpty().toMutableSet()
        if (current.remove(clean)) {
            prefs.edit().putStringSet(KEY_VERIFIED_EMAILS, current).commit()
        }
    }

    @Synchronized
    override fun isEmailVerified(email: String): Boolean {
        val clean = email.trim().lowercase()
        if (clean.isEmpty()) return false
        return prefs.getStringSet(KEY_VERIFIED_EMAILS, emptySet()).orEmpty().contains(clean)
    }

    @Synchronized
    override fun savePendingCode(pending: StoredPendingCode?) {
        if (pending == null) {
            prefs.edit().remove(KEY_PENDING_CODE).commit()
            return
        }
        val json = JSONObject()
            .put("email", pending.email)
            .put("code", pending.code)
            .put("exp", pending.expiresAt.epochSecond)
            .put("att", pending.attempts)
        prefs.edit().putString(KEY_PENDING_CODE, encryptJson(json)).commit()
    }

    @Synchronized
    override fun loadPendingCode(): StoredPendingCode? {
        val stored = prefs.getString(KEY_PENDING_CODE, null) ?: return null
        return try {
            val json = decryptJson(stored)
            StoredPendingCode(
                email = json.getString("email"),
                code = json.getString("code"),
                expiresAt = Instant.ofEpochSecond(json.getLong("exp")),
                attempts = json.optInt("att", 0),
            )
        } catch (e: Exception) {
            prefs.edit().remove(KEY_PENDING_CODE).commit()
            null
        }
    }

    @Synchronized
    override fun savePendingSignUp(pending: StoredPendingSignUp?) {
        if (pending == null) {
            prefs.edit().remove(KEY_PENDING_SIGNUP).commit()
            return
        }
        val json = JSONObject()
            .put("name", pending.fullName)
            .put("email", pending.email)
            .put("phone", pending.phone)
            .put("pw", pending.password)
            .put("code", pending.code)
            .put("exp", pending.expiresAt.epochSecond)
            .put("att", pending.attempts)
        prefs.edit().putString(KEY_PENDING_SIGNUP, encryptJson(json)).commit()
    }

    @Synchronized
    override fun loadPendingSignUp(): StoredPendingSignUp? {
        val stored = prefs.getString(KEY_PENDING_SIGNUP, null) ?: return null
        return try {
            val json = decryptJson(stored)
            StoredPendingSignUp(
                fullName = json.optString("name", ""),
                email = json.getString("email"),
                phone = json.optString("phone", ""),
                password = json.optString("pw", ""),
                code = json.getString("code"),
                expiresAt = Instant.ofEpochSecond(json.getLong("exp")),
                attempts = json.optInt("att", 0),
            )
        } catch (e: Exception) {
            prefs.edit().remove(KEY_PENDING_SIGNUP).commit()
            null
        }
    }

    private fun encryptJson(json: JSONObject): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptJson(stored: String): JSONObject {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return JSONObject(String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8))
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "auth_session_secure"
        const val KEY_BLOB = "blob"
        const val KEY_VERIFIED_EMAILS = "verified_emails"
        const val KEY_PENDING_CODE = "pending_code"
        const val KEY_PENDING_SIGNUP = "pending_signup"
        const val KEY_ALIAS = "truckpark_session_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
