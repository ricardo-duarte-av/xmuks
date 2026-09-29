package pt.aguiarvieira.xmuks.core.data.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.data.media.MediaUploader
import pt.aguiarvieira.xmuks.core.data.media.UploadSource
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.data.timeline.str
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException

/**
 * Profiles: anyone's (`get_profile`), and our own to edit (`set_profile_field`, uploads for the
 * avatar and banner, and the per-message profiles in account data).
 */
class ProfileRepository(
    private val exec: ExecClient,
    private val uploader: MediaUploader,
    database: XmuksDatabase,
    private val ingestor: SyncIngestor,
) {
    private val dao = database.roomListDao()

    /** Our user ID; null until the first sync has told us. */
    val me: Flow<String?> = dao.meta().map { it?.userId }.distinctUntilChanged()

    val perMessageProfiles: Flow<PerMessageProfiles> = perMessageProfiles("")

    /** Per-message profiles for one room ([roomId] "" = the global ones). */
    fun perMessageProfiles(roomId: String): Flow<PerMessageProfiles> =
        dao
            .accountData(roomId, PerMessageProfiles.TYPE)
            .map { json ->
                PerMessageProfiles.parse(
                    json?.let { runCatching { GomuksJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
                )
            }.distinctUntilChanged()

    suspend fun load(userId: String): Result<UserProfile> {
        val params = buildJsonObject { put("user_id", JsonPrimitive(userId)) }
        return exec.exec("get_profile", params, ExecMode.Read).toResult().mapCatching { data ->
            val profile = UserProfile.parse(userId, data as? JsonObject ?: error("No profile"))
            // Ours: the room list header shows it too.
            if (userId == me.first()) ingestor.updateOwnProfile(userId, profile.displayName, profile.avatarMxc)
            profile
        }
    }

    /** Sets one field of our profile; a null [value] removes it. */
    suspend fun setField(
        field: String,
        value: JsonElement?,
    ): Result<Unit> {
        val params =
            buildJsonObject {
                put("field", JsonPrimitive(field))
                value?.let { put("value", it) }
            }
        return exec.exec("set_profile_field", params, ExecMode.Write).toResult().map { }
    }

    suspend fun setText(
        field: String,
        value: String?,
    ) = setField(field, value?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive))

    /** Markdown, rendered by gomuks into the biography; blank removes it. */
    suspend fun setBio(markdown: String): Result<Unit> =
        if (markdown.isBlank()) {
            setField(ProfileFields.BIO_UNSTABLE, null)
        } else {
            setField(ProfileFields.BIO_GOMUKS, JsonPrimitive(markdown))
        }

    suspend fun savePerMessageProfiles(
        profiles: PerMessageProfiles,
        roomId: String? = null,
    ): Result<Unit> {
        val params =
            buildJsonObject {
                roomId?.let { put("room_id", JsonPrimitive(it)) }
                put("type", JsonPrimitive(PerMessageProfiles.TYPE))
                put("content", profiles.toJson())
            }
        return exec.exec("set_account_data", params, ExecMode.Write).toResult().map { }
    }

    /** Uploads [bytes] unencrypted (profile media is public) and returns its `mxc://` URI. */
    suspend fun upload(
        filename: String,
        mimeType: String?,
        bytes: ByteArray,
    ): Result<String> =
        uploader
            .upload(UploadSource(bytes.size.toLong()) { bytes.inputStream() }, filename, mimeType, encrypt = false)
            .mapCatching { it.str("url") ?: throw IOException("No URL") }
}
