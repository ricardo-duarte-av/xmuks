package pt.aguiarvieira.xmuks.core.data.rooms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.profile.Contacts
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.database.InvitedRoomEntity
import pt.aguiarvieira.xmuks.core.database.RoomListDao
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * A room we've been invited to, as its stripped invite state tells it: what it is (a DM, a room, a
 * space), who invited us, and what we'd be joining. The state comes from the inviter's server and
 * is unverified until we join.
 */
data class Invite(
    val roomId: String,
    /** When we were invited (or, failing that, when gomuks got the invite). */
    val createdAt: Long,
    /** The room's name; for a DM without one, the inviter's. */
    val name: String,
    /** The room's avatar; for a DM without one, the inviter's. */
    val avatarMxc: String?,
    val inviterId: String?,
    val inviterName: String?,
    val inviterAvatarMxc: String?,
    /** The inviter marked it a DM (`is_direct` on our invite). */
    val isDirect: Boolean,
    val isSpace: Boolean,
    /** `m.megolm.v1.aes-sha2`, say; null for an unencrypted room. */
    val encryption: String?,
    val joinRule: String?,
    val roomVersion: String?,
    val canonicalAlias: String?,
    val topic: String?,
    /** Members already in, as far as the invite state shows them. */
    val joinedMembers: Int,
    /** Why we were invited, if the inviter said. */
    val reason: String?,
)

/** Pending invites: listed, accepted (joined), declined (left), or declined and the inviter ignored. */
class InvitesRepository(
    private val dao: RoomListDao,
    private val rooms: RoomInfoRepository,
    private val contacts: Contacts,
    val media: MediaUrls,
) {
    /** Newest first. */
    fun invites(): Flow<List<Invite>> = dao.invites().map { list -> list.map(::inviteOf) }

    fun invite(roomId: String): Flow<Invite?> = dao.invite(roomId).map { it?.let(::inviteOf) }

    /** Joins; through the inviter's server, which is certain to be in the room. */
    suspend fun accept(invite: Invite): Result<String> =
        rooms.join(invite.roomId, listOfNotNull(invite.inviterId?.let(::serverOf)))

    suspend fun decline(invite: Invite): Result<Unit> = rooms.leave(invite.roomId)

    /** Declines, and ignores the inviter (spam): nothing more from them is shown. */
    suspend fun declineAndIgnore(invite: Invite): Result<Unit> =
        decline(invite).mapCatching {
            invite.inviterId?.let { contacts.setIgnored(it, true).getOrThrow() }
        }

    /** The rooms we're in with the inviter (their server has to support it). */
    suspend fun sharedRooms(invite: Invite): Result<List<RoomSummary>> =
        invite.inviterId?.let { contacts.mutualRooms(it) } ?: Result.success(emptyList())

    private fun serverOf(userId: String) = userId.substringAfter(':', "").takeIf { it.isNotEmpty() }
}

/** Reads an invite out of its stored stripped state. */
fun inviteOf(entity: InvitedRoomEntity): Invite {
    val state =
        runCatching { GomuksJson.parseToJsonElement(entity.inviteState) as? JsonArray }
            .getOrNull()
            .orEmpty()
            .mapNotNull { it as? JsonObject }

    fun content(
        type: String,
        stateKey: String = "",
    ) = state.lastOrNull { it.text("type") == type && it.raw("state_key") == stateKey }?.get("content") as? JsonObject

    val members = state.filter { it.text("type") == "m.room.member" }
    val ours =
        members.lastOrNull { (it["content"] as? JsonObject)?.text("membership") == "invite" }
    val inviterId = entity.inviter ?: ours?.text("sender")
    val inviter = inviterId?.let { content("m.room.member", it) }
    val inviterName = inviter?.text("displayname")
    val inviterAvatar = inviter?.text("avatar_url")
    val roomName = content("m.room.name")?.text("name")
    val alias = content("m.room.canonical_alias")?.text("alias")
    val isDirect = (ours?.get("content") as? JsonObject)?.flag("is_direct") == true || entity.isDirect
    val create = content("m.room.create")
    return Invite(
        roomId = entity.roomId,
        createdAt = entity.createdAt,
        name = roomName ?: alias?.takeUnless { isDirect } ?: inviterName ?: inviterId ?: entity.roomId,
        avatarMxc = content("m.room.avatar")?.text("url") ?: inviterAvatar.takeIf { isDirect },
        inviterId = inviterId,
        inviterName = inviterName,
        inviterAvatarMxc = inviterAvatar,
        isDirect = isDirect,
        isSpace = create?.text("type") == "m.space",
        encryption = content("m.room.encryption")?.text("algorithm"),
        joinRule = content("m.room.join_rules")?.text("join_rule"),
        roomVersion = create?.text("room_version"),
        canonicalAlias = alias,
        topic = content("m.room.topic")?.text("topic"),
        joinedMembers = members.count { (it["content"] as? JsonObject)?.text("membership") == "join" },
        reason = (ours?.get("content") as? JsonObject)?.text("reason"),
    )
}

private fun JsonObject.text(key: String) =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotEmpty() }

/** As it is: room-wide state has an empty state key, which [text] would call missing. */
private fun JsonObject.raw(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull
