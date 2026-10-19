package pt.aguiarvieira.xmuks.core.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
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
 * it's renewed daily (when the stream goes live), and at once when the token or gateway changes.
 *
 * The token goes as `{token, app_id, gateway}`: the push gateway delivers for our Firebase app
 * ([appId]), and gomuks only sends through a gateway it's configured with or whitelists, so it's
 * named: gomuks' own by default, or the one set on this device ([setGateway]).
 */
class PushRegistrar(
    private val exec: ExecClient,
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val tokens: PushTokenSource,
    private val appId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountScoped {
    private val lock = Mutex()

    /** A token from Firebase (new, or refreshed): registered with gomuks at once. */
    suspend fun tokenReceived(token: String) {
        dataStore.edit { it[FCM_TOKEN] = token }
        ensureRegistered(force = true)
    }

    /** This device's own push gateway (a host), or null for gomuks' default ([DEFAULT_GATEWAY]). */
    val gateway: Flow<String?> = dataStore.data.map { it[GATEWAY] }

    /** Sets this device's push gateway ([gatewayHost] of what's typed; blank = the default) and registers with it. */
    suspend fun setGateway(input: String?) {
        val host = input?.let(::gatewayHost)
        dataStore.edit { if (host == null) it.remove(GATEWAY) else it[GATEWAY] = host }
        ensureRegistered()
    }

    /** Registers when needed: what gomuks has differs (token, gateway), never registered, or getting old. */
    suspend fun ensureRegistered(force: Boolean = false) =
        lock.withLock {
            val prefs = dataStore.data.first()
            val token = prefs[FCM_TOKEN]
            if (token == null) {
                // None yet: Firebase registers, and hands the token to tokenReceived.
                tokens.request()
                return@withLock
            }
            val data = registration(token, prefs[GATEWAY] ?: DEFAULT_GATEWAY).toString()
            val fresh = clock() - (prefs[REGISTERED_AT] ?: 0) < RENEW_AFTER_MS
            if (!force && fresh && prefs[REGISTERED] == data) return@withLock
            if (register(data, deviceId(), key(), expiresAt = clock() + VALID_FOR_MS)) {
                dataStore.edit {
                    it[REGISTERED] = data
                    it[REGISTERED_AT] = clock()
                    it.remove(LEGACY_TOKEN)
                }
            }
        }

    private fun registration(
        token: String,
        gateway: String,
    ) = buildJsonObject {
        put("token", JsonPrimitive(token))
        put("app_id", JsonPrimitive(appId))
        put("gateway", JsonPrimitive(gateway))
    }

    /** The AES key pushes are encrypted with, for decrypting them; null before the first registration. */
    suspend fun pushKey(): ByteArray? =
        dataStore.data.first()[KEY]?.let { runCatching { cipher.decrypt(Base64.getDecoder().decode(it)) }.getOrNull() }

    /** Logging out: expire this device's registration (best effort: gomuks may be unreachable). */
    override suspend fun beforeLogout() {
        val prefs = dataStore.data.first()
        // Re-sent as gomuks has it (a plain token, from before the object form).
        val data = prefs[REGISTERED] ?: prefs[LEGACY_TOKEN]?.let { JsonPrimitive(it).toString() } ?: return
        val key = pushKey() ?: return
        withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { register(data, deviceId(), key, expiresAt = clock()) }
    }

    override suspend fun clearAccountData() {
        // The device ID, key and gateway can stay (they're this install's); the registration state can't.
        dataStore.edit {
            it.remove(REGISTERED)
            it.remove(LEGACY_TOKEN)
            it.remove(REGISTERED_AT)
        }
    }

    /** [data]: the registration's `data`, as JSON. */
    private suspend fun register(
        data: String,
        deviceId: String,
        key: ByteArray,
        expiresAt: Long,
    ): Boolean {
        val params =
            buildJsonObject {
                put("device_id", JsonPrimitive(deviceId))
                put("type", JsonPrimitive("fcm"))
                put("data", Json.parseToJsonElement(data))
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

        /** The registration `data` gomuks has (JSON), as opposed to the latest token from Firebase ([FCM_TOKEN]). */
        val REGISTERED = stringPreferencesKey("registered_data")

        /** Before the object form: the plain token gomuks had. */
        val LEGACY_TOKEN = stringPreferencesKey("token")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val GATEWAY = stringPreferencesKey("gateway")
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

/** gomuks' own push gateway, which delivers for xmuks too. */
const val DEFAULT_GATEWAY = "push.gomuks.app"

/**
 * The host gomuks wants as a push gateway, from what someone typed: no scheme, no path, no trailing
 * slash (`https://push.example.org/` → `push.example.org`). Null when it's blank or the default.
 */
fun gatewayHost(input: String): String? =
    input
        .trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .lowercase()
        .takeIf { it.isNotEmpty() && it != DEFAULT_GATEWAY }
