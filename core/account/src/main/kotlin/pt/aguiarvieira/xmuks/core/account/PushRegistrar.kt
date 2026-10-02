package pt.aguiarvieira.xmuks.core.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Where push tokens come from (Firebase), kept out of this module. Asking only starts it: the
 * token arrives later, through [PushRegistrar.tokenReceived].
 */
fun interface PushTokenSource {
    fun request()
}

/**
 * Keeps gomuks pushing to this device (`register_push`): a stable device ID, the FCM token, and a
 * 32-byte AES-GCM key gomuks encrypts every push with (the gateway and Google only see ciphertext).
 * The key is stored encrypted by the Keystore. gomuks drops a registration after its expiry, so
 * it's renewed daily (when the stream goes live), and at once when the token changes.
 */
class PushRegistrar(
    private val exec: ExecClient,
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val tokens: PushTokenSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountScoped {
    private val lock = Mutex()

    /** A token from Firebase (new, or refreshed): registered with gomuks at once. */
    suspend fun tokenReceived(token: String) {
        dataStore.edit { it[FCM_TOKEN] = token }
        ensureRegistered(force = true)
    }

    /** Registers when needed: a new token, never registered, or the last one getting old. */
    suspend fun ensureRegistered(force: Boolean = false) =
        lock.withLock {
            val prefs = dataStore.data.first()
            val token = prefs[FCM_TOKEN]
            if (token == null) {
                // None yet: Firebase registers, and hands the token to tokenReceived.
                tokens.request()
                return@withLock
            }
            val fresh = clock() - (prefs[REGISTERED_AT] ?: 0) < RENEW_AFTER_MS
            if (!force && fresh && prefs[TOKEN] == token) return@withLock
            if (register(token, deviceId(), key(), expiresAt = clock() + VALID_FOR_MS)) {
                dataStore.edit {
                    it[TOKEN] = token
                    it[REGISTERED_AT] = clock()
                }
            }
        }

    /** The AES key pushes are encrypted with, for decrypting them; null before the first registration. */
    suspend fun pushKey(): ByteArray? =
        dataStore.data.first()[KEY]?.let { runCatching { cipher.decrypt(Base64.getDecoder().decode(it)) }.getOrNull() }

    /** Logging out: expire this device's registration (best effort: gomuks may be unreachable). */
    override suspend fun beforeLogout() {
        val prefs = dataStore.data.first()
        val token = prefs[TOKEN] ?: return
        val key = pushKey() ?: return
        withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { register(token, deviceId(), key, expiresAt = clock()) }
    }

    override suspend fun clearAccountData() {
        // The device ID and key can stay (they're this install's); the registration state can't.
        dataStore.edit {
            it.remove(TOKEN)
            it.remove(REGISTERED_AT)
        }
    }

    private suspend fun register(
        token: String,
        deviceId: String,
        key: ByteArray,
        expiresAt: Long,
    ): Boolean {
        val params =
            buildJsonObject {
                put("device_id", JsonPrimitive(deviceId))
                put("type", JsonPrimitive("fcm"))
                put("data", JsonPrimitive(token))
                put(
                    "encryption",
                    buildJsonObject { put("key", JsonPrimitive(Base64.getEncoder().encodeToString(key))) }
                )
                put("expiration", JsonPrimitive(expiresAt / MS_PER_SECOND))
            }
        return exec.exec("register_push", params, ExecMode.Write) is ExecResult.Ok
    }

    private suspend fun deviceId(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }
        val id = "xmuks-" + UUID.randomUUID()
        dataStore.edit { it[DEVICE_ID] = id }
        return id
    }

    private suspend fun key(): ByteArray {
        pushKey()?.let { return it }
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        dataStore.edit { it[KEY] = Base64.getEncoder().encodeToString(cipher.encrypt(key)) }
        return key
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val KEY = stringPreferencesKey("key")

        /** The token gomuks has, as opposed to the latest from Firebase ([FCM_TOKEN]). */
        val TOKEN = stringPreferencesKey("token")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val REGISTERED_AT = longPreferencesKey("registered_at")
        const val KEY_BYTES = 32
        const val MS_PER_SECOND = 1000
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** gomuks keeps a registration this long; renewed well before, every day. */
        const val VALID_FOR_MS = 7 * DAY_MS
        const val RENEW_AFTER_MS = DAY_MS
        const val LOGOUT_TIMEOUT_MS = 3_000L
    }
}
