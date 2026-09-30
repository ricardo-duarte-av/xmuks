package pt.aguiarvieira.xmuks.core.push

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.RoomShortcuts
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary

/**
 * A room on the home screen: the same shortcut notifications publish for it (its ID, avatar and
 * `matrix:` link), pinned through the launcher's own confirmation.
 */
class PinnedShortcuts(
    private val context: Context,
    private val images: ImageLoader,
    private val media: MediaUrls,
) : RoomShortcuts {
    override suspend fun pin(room: RoomSummary): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val shortcut =
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
                .build()
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }.getOrDefault(false)
    }

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
    }
}
