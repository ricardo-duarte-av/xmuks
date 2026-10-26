package pt.aguiarvieira.xmuks.core.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.RoomShortcuts
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.notify.Avatars
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier

/**
 * A room on the home screen: the same shortcut notifications publish for it (its ID, avatar and
 * `matrix:` link), pinned through the launcher's own confirmation. Shortcuts already made (pinned,
 * or kept by the system from notifications) follow their room's name and avatar ([follow]).
 */
class PinnedShortcuts(
    private val context: Context,
    private val images: ImageLoader,
    private val media: MediaUrls,
) : RoomShortcuts {
    override suspend fun pin(room: RoomSummary): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val shortcut = shortcut(room)
        return runCatching {
            // Pinning an ID the system already has pins what it has: brought up to date first.
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }

    /**
     * Keeps the shortcuts there are in step with [rooms]: one whose room's name or avatar changed
     * is updated (the avatar it was made with is kept in its extras, to tell).
     */
    @OptIn(FlowPreview::class)
    fun follow(
        rooms: Flow<List<RoomSummary>>,
        scope: CoroutineScope,
    ) {
        scope.launch {
            rooms.debounce(SETTLE_MS).collect { list ->
                runCatching { withContext(Dispatchers.IO) { update(list) } }
            }
        }
    }

    private suspend fun update(rooms: List<RoomSummary>) {
        val existing = ShortcutManagerCompat.getShortcuts(context, KINDS)
        if (existing.isEmpty()) return
        val byId = rooms.associateBy { it.roomId }
        val stale =
            existing.mapNotNull { shortcut ->
                val room = byId[shortcut.id] ?: return@mapNotNull null
                val madeWith = shortcut.extras?.getString(AVATAR)
                // A notification's label is gomuks' name, cut at 50 characters with "…".
                val named = room.name.startsWith(shortcut.shortLabel.toString().removeSuffix("…"))
                room.takeIf { !named || it.avatarMxc.orEmpty() != madeWith }
            }
        if (stale.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(context, stale.map { shortcut(it) })
    }

    private suspend fun shortcut(room: RoomSummary): ShortcutInfoCompat =
        ShortcutInfoCompat
            .Builder(context, room.roomId)
            .setShortLabel(room.name)
            .setLongLived(true)
            .setIcon(Avatars.adaptive(avatar(room.avatarMxc), room.name, room.roomId))
            .setLocusId(LocusIdCompat(room.roomId))
            .setIntent(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(LinkResolver.roomUri(room.roomId))
                ).setPackage(context.packageName),
            ).setCategories(setOf(RoomNotifier.SHORTCUT_CATEGORY))
            .setExtras(PersistableBundle().apply { putString(AVATAR, room.avatarMxc.orEmpty()) })
            .build()

    /** The room's avatar thumbnail, through the app's own (authenticated, cached) image loader. */
    private suspend fun avatar(mxc: String?) =
        media.avatar(mxc)?.let { url ->
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(AVATAR_PX)
                    .allowHardware(false)
                    .build()
            (images.execute(request) as? SuccessResult)?.image?.toBitmap()
        }

    private companion object {
        const val AVATAR_PX = 192

        /** The extra holding the avatar (mxc) a shortcut was made with; empty for none. */
        const val AVATAR = "xmuks.avatar"
        const val SETTLE_MS = 2_000L
        const val KINDS =
            ShortcutManagerCompat.FLAG_MATCH_PINNED or
                ShortcutManagerCompat.FLAG_MATCH_CACHED or
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
    }
}
