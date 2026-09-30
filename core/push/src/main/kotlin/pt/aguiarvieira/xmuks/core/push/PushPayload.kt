package pt.aguiarvieira.xmuks.core.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** What gomuks pushes (`PushNotification`): new messages, rooms read elsewhere, a media token. */
@Serializable
data class PushPayload(
    val messages: List<PushMessage> = emptyList(),
    val dismiss: List<PushDismiss> = emptyList(),
    /** Appended as `?image_auth=` to gomuks media URLs: avatars load without our session. */
    @SerialName("image_auth") val imageAuth: String? = null,
)

@Serializable
data class PushMessage(
    val timestamp: Long,
    @SerialName("event_id") val eventId: String,
    @SerialName("room_id") val roomId: String,
    /** gomuks' name for the room: for a room without one, built from its members (bots left out). */
    @SerialName("room_name") val roomName: String,
    @SerialName("room_avatar") val roomAvatar: String? = null,
    /** gomuks says it's a DM (sent only when true, and only by gomuks from 2026-09-29 on). */
    @SerialName("is_dm") val isDm: Boolean = false,
    val sender: PushUser,
    val self: PushUser,
    val text: String,
    val image: String? = null,
    val mention: Boolean = false,
    val reply: Boolean = false,
    /** The push rules asked for a sound. */
    val sound: Boolean = false,
)

@Serializable
data class PushUser(
    val id: String,
    val name: String,
    /** A gomuks media path (`_gomuks/media/…?encrypted=false&fallback=…`), relative to the server. */
    val avatar: String? = null,
)

/** The room was read up to [readUpTo] elsewhere: its notification can go. */
@Serializable
data class PushDismiss(
    @SerialName("room_id") val roomId: String,
    @SerialName("read_up_to") val readUpTo: String? = null,
)

/**
 * Opens a push: base64 of `iv (12 bytes) ‖ AES-256-GCM ciphertext+tag` under our [key] (as gomuks'
 * `encryptPush` seals it), then JSON. Null for anything that doesn't decrypt or parse.
 */
object PushCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun open(
        payload: String,
        key: ByteArray,
    ): PushPayload? =
        runCatching {
            val sealed = Base64.getDecoder().decode(payload)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            val plain = cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
            json.decodeFromString(PushPayload.serializer(), plain.decodeToString())
        }.getOrNull()

    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
}
