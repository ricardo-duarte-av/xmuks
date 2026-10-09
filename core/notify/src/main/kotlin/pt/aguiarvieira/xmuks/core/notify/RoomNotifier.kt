package pt.aguiarvieira.xmuks.core.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl
import pt.aguiarvieira.xmuks.core.protocol.Event
import java.util.concurrent.ConcurrentHashMap

/**
 * Message notifications, one conversation per room: the people in it (Person, with avatars), a
 * long-lived shortcut per room (Conversations section, per-conversation settings, and Android's
 * own bubbles), messages appended as they come (never twice), the DM or group channel — or the
 * quiet one when the push rules said no sound — and opening the room when tapped.
 */
class RoomNotifier(
    private val context: Context,
    private val images: ImageLoader,
    private val actions: Actions,
    /**
     * Opens a room in the app: tapping the notification does, and the room becomes a conversation
     * shortcut. Null where there's nothing to open (the watch): no shortcuts then either.
     */
    private val roomIntent: ((roomId: String) -> Intent)? = null,
    private val server: () -> HttpUrl?,
    /** Whether a room is a DM, from our cache; null when we don't know the room yet. */
    private val isDirect: suspend (roomId: String) -> Boolean?,
    /** Whether the room is muted by its push rules (mentions, `@room` and keywords make no sound then). */
    private val isMuted: suspend (roomId: String) -> Boolean = { false },
    /** The whole event (gomuks' `get_event`), for its formatting and media; null when it can't be had. */
    private val eventOf: suspend (roomId: String, eventId: String) -> Event? = { _, _ -> null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Rooms replied to from their notification: gomuks' dismissal (it marks them read) is ignored meanwhile. */
    private val guardUntil = ConcurrentHashMap<String, Long>()

    suspend fun show(
        payload: PushPayload,
        /** The room on screen: nothing for it. */
        openRoom: String?,
    ) {
        Channels.ensure(context)
        payload.dismiss.forEach { dismissed(it.roomId) }
        payload.messages
            .filter { it.roomId != openRoom }
            .groupBy { it.roomId }
            .forEach { (roomId, messages) -> showRoom(roomId, messages.sortedBy { it.timestamp }, payload.imageAuth) }
    }

    /** Read elsewhere: the notification goes, unless we just replied from it. */
    fun dismissed(roomId: String) {
        if ((guardUntil[roomId] ?: 0) > clock()) return
        clear(roomId)
    }

    fun clear(roomId: String) = manager.cancel(roomId, NOTIFICATION_ID)

    /** Our reply from the notification: shown in it at once, and the dismissal it causes held off. */
    fun replied(
        roomId: String,
        text: String,
    ) {
        guardUntil[roomId] = clock() + REPLY_GUARD_MS
        val current = active(roomId) ?: return
        val style = MessagingStyle.extractMessagingStyleFromNotification(current) ?: return
        style.addMessage(MessagingStyle.Message(text, clock(), null as Person?))
        val builder =
            NotificationCompat
                .Builder(context, current)
                .setStyle(style)
                .setOnlyAlertOnce(true)
                .setSilent(true)
        post(roomId, builder.build())
    }

    private suspend fun showRoom(
        roomId: String,
        messages: List<PushMessage>,
        imageAuth: String?,
    ) {
        val latest = messages.last()
        // gomuks' own word when it gives it (is_dm, only ever true); otherwise our cache, then a guess.
        val direct = latest.isDm || (isDirect(roomId) ?: (latest.roomName == latest.sender.name))
        val existing = active(roomId)?.let(MessagingStyle::extractMessagingStyleFromNotification)
        val seen =
            existing
                ?.messages
                ?.mapNotNull { it.extras.getString(EVENT_ID) }
                ?.toSet()
                .orEmpty()
        val fresh = messages.filter { it.eventId !in seen }
        if (fresh.isEmpty()) return
        val style =
            (existing ?: MessagingStyle(person(latest.self, imageAuth)))
                .setGroupConversation(!direct)
                .setConversationTitle(if (direct) null else latest.roomName)
        val (people, highlighted) = addMessages(style, fresh, imageAuth)
        val avatar = loadBitmap(latest.roomAvatar, imageAuth)
        val face = face(latest, direct, avatar, imageAuth)
        val intent = roomIntent?.invoke(roomId)
        if (intent != null) {
            publishShortcut(
                roomId,
                latest.roomName,
                Avatars.adaptive(avatar, latest.roomName, roomId),
                if (direct) people.values else emptyList(),
                intent,
            )
        }
        // Loud when the push rules asked for a sound, or when it's for us (a mention, @room, a
        // keyword: highlighted, which the default rules leave silent) in a room we haven't muted.
        val loud = fresh.any { it.sound } || (highlighted && !isMuted(roomId))
        val channel = Channels.conversation(context, roomId, latest.roomName, direct)
        val notification =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setStyle(style)
                .setLargeIcon(face)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setWhen(latest.timestamp)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setSilent(!loud)
                .apply {
                    if (intent != null) {
                        setShortcutId(roomId)
                        setLocusId(LocusIdCompat(roomId))
                        setContentIntent(openRoom(roomId, intent))
                    }
                    actions.add(this, context, roomId, latest.eventId)
                }.build()
        post(roomId, notification)
    }

    /**
     * The conversation's face (Android Auto shows it, the shade too): a group's own avatar; in a
     * DM, the other person's (usually the room's too, which stands in when theirs won't load).
     */
    private suspend fun face(
        latest: PushMessage,
        direct: Boolean,
        roomAvatar: Bitmap?,
        imageAuth: String?,
    ): Bitmap =
        if (direct) {
            Avatars.roundBitmap(
                loadBitmap(latest.sender.avatar, imageAuth) ?: roomAvatar,
                latest.sender.name,
                latest.sender.id,
            )
        } else {
            Avatars.roundBitmap(roomAvatar, latest.roomName, latest.roomId)
        }

    /**
     * Adds [fresh] to [style] (a picture as itself, then its caption); the senders met, by ID, and
     * whether any of them highlighted us (a mention, @room, a keyword).
     */
    private suspend fun addMessages(
        style: MessagingStyle,
        fresh: List<PushMessage>,
        imageAuth: String?,
    ): Pair<Map<String, Person>, Boolean> {
        val people = HashMap<String, Person>()
        var highlighted = false
        fresh.forEach { m ->
            val sender = people.getOrPut(m.sender.id) { person(m.sender, imageAuth) }
            val event = withTimeoutOrNull(EVENT_MS) { eventOf(m.roomId, m.eventId) }
            if (m.mention || (event?.unreadType ?: 0) and HIGHLIGHT != 0 ||
                event?.mentionsRoom() == true
            ) {
                highlighted = true
            }
            val shown = event?.let { shownOf(it, m) } ?: shownOf(m)
            val message =
                MessagingStyle
                    .Message(
                        shown.text,
                        m.timestamp,
                        sender
                    ).apply { extras.putString(EVENT_ID, m.eventId) }
            val picture = shown.picture?.let { pictureUri(it, m.eventId, imageAuth) }
            if (picture != null) message.setData(JPEG, picture)
            style.addMessage(message)
            // A message with a picture shows only the picture: its caption follows as a line of its own.
            shown.caption
                ?.takeUnless { it.toString().looksLikeFileName() }
                ?.let { style.addMessage(MessagingStyle.Message(it, m.timestamp, sender)) }
        }
        return people to highlighted
    }

    private suspend fun person(
        user: PushUser,
        imageAuth: String?,
    ): Person =
        Person
            .Builder()
            .setKey(user.id)
            .setName(user.name)
            .setUri(PeopleUris.forUser(context, user.id))
            .setIcon(Avatars.round(loadBitmap(user.avatar, imageAuth), user.name, user.id))
            .build()

    private fun publishShortcut(
        roomId: String,
        name: String,
        icon: androidx.core.graphics.drawable.IconCompat,
        people: Collection<Person>,
        intent: Intent,
    ) {
        val shortcut =
            ShortcutInfoCompat
                .Builder(context, roomId)
                .setShortLabel(name)
                .setLongLived(true)
                .setIcon(icon)
                .setLocusId(LocusIdCompat(roomId))
                .setIntent(intent)
                .setPersons(people.toTypedArray())
                .setCategories(setOf(SHORTCUT_CATEGORY))
                .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }

    private fun openRoom(
        roomId: String,
        intent: Intent,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            roomId.hashCode(),
            Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * A gomuks media path (relative to the server), with the push's token so it loads without our
     * session. The image loader caches by mxc:// (MxcCacheKeys), so an avatar the app has shown
     * loads from disk, and one fetched here is there for the app. A failed fetch is tried once more.
     */
    private suspend fun loadBitmap(
        path: String?,
        imageAuth: String?,
        sizePx: Int = AVATAR_PX,
    ): Bitmap? {
        val base = server() ?: return null
        val resolved = path?.let(base::resolve) ?: return null
        val url =
            resolved
                .newBuilder()
                .apply {
                    if (sizePx == AVATAR_PX) addQueryParameter("thumbnail", "avatar")
                    imageAuth?.let { addQueryParameter("image_auth", it) }
                }.build()
                .toString()
        val request =
            ImageRequest
                .Builder(context)
                .data(url)
                .size(sizePx)
                .allowHardware(false)
                .build()
        repeat(ATTEMPTS) { attempt ->
            (images.execute(request) as? SuccessResult)?.image?.toBitmap()?.let { return it }
            if (attempt < ATTEMPTS - 1) delay(RETRY_MS)
        }
        return null
    }

    /**
     * A message's picture, saved where the system UI can read it (our FileProvider): a
     * notification can only show images it's handed as content URIs. Old ones are cleared.
     */
    private suspend fun pictureUri(
        path: String,
        eventId: String,
        imageAuth: String?,
    ): Uri? {
        val bitmap = loadBitmap(path, imageAuth, PICTURE_PX) ?: return null
        val dir = java.io.File(context.cacheDir, PICTURES).apply { mkdirs() }
        dir.listFiles()?.filter { clock() - it.lastModified() > PICTURE_TTL_MS }?.forEach { it.delete() }
        val file = java.io.File(dir, "${eventId.hashCode().toUInt()}.jpg")
        runCatching {
            file.outputStream().use {
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    JPEG_QUALITY,
                    it
                )
            }
        }.getOrNull()
            ?: return null
        return androidx.core.content.FileProvider
            .getUriForFile(context, context.packageName + ".notifications", file)
    }

    private fun String.looksLikeFileName() = isBlank() || FILE_NAME.matches(trim())

    private fun active(roomId: String): Notification? =
        manager.activeNotifications.firstOrNull { it.tag == roomId && it.id == NOTIFICATION_ID }?.notification

    @SuppressLint("MissingPermission") // checked just above
    private fun post(
        roomId: String,
        notification: Notification,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(roomId, NOTIFICATION_ID, notification)
    }

    companion object {
        /** Notifications are tagged with their room ID; the ID itself is constant. */
        const val NOTIFICATION_ID = 1
        const val EVENT_ID = "xmuks.event_id"
        const val SHORTCUT_CATEGORY = "pt.aguiarvieira.xmuks.category.ROOM"

        /** gomuks marks a room read when we reply: its dismissal is ignored for this long after. */
        const val REPLY_GUARD_MS = 10_000L
        private const val AVATAR_PX = 192

        /** gomuks' unread type bit for a highlighting push rule (mentions, @room, keywords). */
        private const val HIGHLIGHT = 0b0100

        /** How long a message waits for its full event before showing what the push said. */
        private const val EVENT_MS = 3_000L
        private const val ATTEMPTS = 2
        private const val RETRY_MS = 1_500L

        private const val PICTURE_PX = 1024
        private const val JPEG = "image/jpeg"
        private val FILE_NAME = Regex("""[^\s/]+\.[A-Za-z0-9]{2,5}""")
        private const val JPEG_QUALITY = 85
        private const val PICTURES = "notification-images"
        private const val PICTURE_TTL_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * An @room (MSC3952's `m.mentions.room`). Its push rule notifies but, by default, neither
 * highlights nor sounds; it only matched if the sender may ping the room.
 */
private fun Event.mentionsRoom(): Boolean =
    ((effectiveContent["m.mentions"] as? JsonObject)?.get("room") as? JsonPrimitive)?.booleanOrNull == true
