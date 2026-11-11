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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.account.AccountScoped
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.rooms.OwnProfile
import pt.aguiarvieira.xmuks.core.data.rooms.RoomShortcuts
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.notify.Avatars
import pt.aguiarvieira.xmuks.core.notify.RoomKeys
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier
import pt.aguiarvieira.xmuks.core.notify.ShortcutStamps

/**
 * A room on the home screen: the same shortcut notifications publish for it (its ID, avatar and
 * `matrix:` link), pinned through the launcher's own confirmation. Shortcuts already made (pinned,
 * or kept by the system from notifications) follow their room's name and avatar ([follow]).
 */
class PinnedShortcuts(
    private val context: Context,
    private val images: ImageLoader,
    private val media: MediaUrls,
    /** The account they're for ([RoomKeys]); null for the first. */
    private val account: String? = null,
    /** Opening a room in the app, as this account. */
    private val roomIntent: (roomId: String) -> Intent = { roomId ->
        Intent(Intent.ACTION_VIEW, Uri.parse(LinkResolver.roomUri(roomId))).setPackage(context.packageName)
    },
    /** The account's own ID (its graph's), for its own shortcut; null: no account shortcut. */
    private val accountId: String? = null,
) : RoomShortcuts,
    AccountScoped {
    override suspend fun pinAccount(
        profile: OwnProfile?,
        login: String,
    ): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val shortcut = accountShortcut(profile, login) ?: return false
        return runCatching {
            // Signed out and back in: its old shortcut was disabled.
            ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
            publishAccount(profile, login)
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }

    /**
     * Keeps the account's own shortcut, if it has one, in step with its [profile]: its name and
     * avatar (the avatar it was made with is kept in its extras, to tell). [login] as for [pinAccount].
     */
    @OptIn(FlowPreview::class)
    fun followAccount(
        profile: Flow<OwnProfile?>,
        login: suspend () -> String,
        scope: CoroutineScope,
    ) {
        val id = accountId ?: return
        scope.launch {
            profile.filterNotNull().debounce(SETTLE_MS).collect { me ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        val existing =
                            ShortcutManagerCompat
                                .getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                                .firstOrNull { it.id == accountShortcutId(id) && it.isEnabled }
                                ?: return@withContext
                        val stale =
                            existing.shortLabel.toString() != me.displayName ||
                                ShortcutStamps.avatarOf(existing.extras) != me.avatarUrl.orEmpty()
                        if (stale) publishAccount(me, login())
                    }
                }
            }
        }
    }

    /** The account's shortcut updated, as [publish] does a room's. */
    private suspend fun publishAccount(
        profile: OwnProfile?,
        login: String,
    ) {
        val shortcut = accountShortcut(profile, login) ?: return
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        delay(ShortcutStamps.ICON_WRITTEN_MS)
        accountShortcut(
            profile,
            login,
            icon = false
        )?.let { ShortcutManagerCompat.updateShortcuts(context, listOf(it)) }
    }

    private suspend fun accountShortcut(
        profile: OwnProfile?,
        login: String,
        icon: Boolean = true,
    ): ShortcutInfoCompat? {
        val id = accountId ?: return null
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        val name = profile?.displayName ?: login
        return ShortcutInfoCompat
            .Builder(context, accountShortcutId(id))
            .setShortLabel(name)
            .setLongLabel(listOfNotNull(profile?.userId, login).joinToString(" · "))
            .apply {
                if (icon) {
                    val avatar = withContext(Dispatchers.IO) { profile?.avatarUrl?.let { bitmap(it) } }
                    setIcon(Avatars.adaptive(avatar, name, profile?.userId ?: login))
                }
            }.setIntent(
                Intent(launch)
                    .putExtra(RoomKeys.EXTRA_ACCOUNT, id)
                    .putExtra(RoomKeys.EXTRA_HOME, true),
            ).setExtras(ShortcutStamps.of(profile?.avatarUrl, asOf = null))
            .build()
    }

    /** Signing out: the account's shortcut stays on the home screen, greyed out, saying why. */
    override suspend fun beforeLogout() {
        val id = accountId ?: return
        runCatching {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(accountShortcutId(id)),
                context.getString(R.string.account_shortcut_signed_out),
            )
        }
    }

    override suspend fun clearAccountData() = Unit

    override suspend fun pin(room: RoomSummary): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val shortcut = shortcut(room)
        return runCatching {
            // Pinning an ID the system already has pins what it has: brought up to date first.
            publish(listOf(room))
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
        // Only this account's: another's may be in the same rooms.
        val existing =
            ShortcutManagerCompat.getShortcuts(context, KINDS).filter { RoomKeys.accountOf(it.id) == account }
        if (existing.isEmpty()) return
        val byId = rooms.associateBy { it.roomId }
        val stale =
            existing.mapNotNull { shortcut ->
                val room = byId[RoomKeys.roomOf(shortcut.id)] ?: return@mapNotNull null
                // Made from a newer view of the room than ours (a push, while we catch up): left be.
                if (ShortcutStamps.asOfOf(shortcut.extras) > room.timestamp) return@mapNotNull null
                val madeWith = ShortcutStamps.avatarOf(shortcut.extras)
                // A notification's label is gomuks' name, cut at 50 characters with "…".
                val named = room.name.startsWith(shortcut.shortLabel.toString().removeSuffix("…"))
                // Made before there could be several accounts: it doesn't say which one opens it.
                val accounted = shortcut.intent.hasExtra(RoomKeys.EXTRA_ACCOUNT)
                room.takeIf { !named || !accounted || it.avatarMxc.orEmpty() != madeWith }
            }
        if (stale.isNotEmpty()) publish(stale)
    }

    /** Updates [rooms]' shortcuts, telling the launcher again once the icons are written ([ShortcutStamps]). */
    private suspend fun publish(rooms: List<RoomSummary>) {
        ShortcutManagerCompat.updateShortcuts(context, rooms.map { shortcut(it) })
        delay(ShortcutStamps.ICON_WRITTEN_MS)
        ShortcutManagerCompat.updateShortcuts(context, rooms.map { shortcut(it, icon = false) })
    }

    private suspend fun shortcut(
        room: RoomSummary,
        icon: Boolean = true,
    ): ShortcutInfoCompat =
        ShortcutInfoCompat
            .Builder(context, RoomKeys.of(account, room.roomId))
            .setShortLabel(room.name)
            .setLongLived(true)
            .apply { if (icon) setIcon(Avatars.adaptive(avatar(room.avatarMxc), room.name, room.roomId)) }
            .setLocusId(LocusIdCompat(RoomKeys.of(account, room.roomId)))
            .setIntent(roomIntent(room.roomId))
            .setCategories(setOf(RoomNotifier.SHORTCUT_CATEGORY))
            .setExtras(ShortcutStamps.of(room.avatarMxc, room.timestamp))
            .build()

    /** The room's avatar thumbnail, through the app's own (authenticated, cached) image loader. */
    private suspend fun avatar(mxc: String?) = media.avatar(mxc)?.let { bitmap(it) }

    private suspend fun bitmap(url: String) =
        ImageRequest
            .Builder(context)
            .data(url)
            .size(AVATAR_PX)
            .allowHardware(false)
            .build()
            .let { (images.execute(it) as? SuccessResult)?.image?.toBitmap() }

    private companion object {
        const val AVATAR_PX = 192
        const val SETTLE_MS = 2_000L
        const val KINDS =
            ShortcutManagerCompat.FLAG_MATCH_PINNED or
                ShortcutManagerCompat.FLAG_MATCH_CACHED or
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
    }
}

/** An account's own shortcut (not a room's: room IDs start with `!`). */
private fun accountShortcutId(account: String) = "account:$account"
