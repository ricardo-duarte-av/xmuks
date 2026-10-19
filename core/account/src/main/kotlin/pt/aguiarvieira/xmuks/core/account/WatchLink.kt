package pt.aguiarvieira.xmuks.core.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How the watch signs in: it asks the phone (Wear Data Layer, which only connects apps with our
 * package name and signing key) and the phone answers with its gomuks login. The watch then logs
 * in for itself and registers its own pusher.
 */
object WatchLink {
    /** Declared by the phone app (res/values/wear.xml): where the watch sends its request. */
    const val PHONE_CAPABILITY = "xmuks_phone"

    /** Declared by the watch app. */
    const val WATCH_CAPABILITY = "xmuks_watch"

    const val LOGIN_PATH = "/xmuks/login"

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(handoff: LoginHandoff): ByteArray =
        json.encodeToString(LoginHandoff.serializer(), handoff).encodeToByteArray()

    /** Null for anything that isn't a handoff (an empty answer: the phone isn't logged in). */
    fun decode(bytes: ByteArray): LoginHandoff? =
        runCatching { json.decodeFromString(LoginHandoff.serializer(), bytes.decodeToString()) }.getOrNull()
}

@Serializable
data class LoginHandoff(
    @SerialName("server_url") val serverUrl: String,
    val username: String,
    val password: String,
    /** The phone's choice: public read receipts, or private ones. */
    @SerialName("send_read_receipts") val sendReadReceipts: Boolean = true,
)
