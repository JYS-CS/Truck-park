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

/** Where the app keeps its sign-in between launches. */
interface SessionStore {
    fun load(): AuthSession?
    fun save(session: AuthSession)
    fun clear()
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
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8))
            AuthSession(
                sessionToken = json.getString("s"),
                refreshToken = json.getString("r"),
                expiresAt = Instant.ofEpochSecond(json.getLong("e")),
            )
        } catch (e: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    override fun save(session: AuthSession) {
        val json = JSONObject()
            .put("s", session.sessionToken)
            .put("r", session.refreshToken)
            .put("e", session.expiresAt.epochSecond)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_BLOB, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP))
            .commit()
    }

    @Synchronized
    override fun clear() {
        prefs.edit().remove(KEY_BLOB).commit()
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
        const val KEY_ALIAS = "truckpark_session_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
