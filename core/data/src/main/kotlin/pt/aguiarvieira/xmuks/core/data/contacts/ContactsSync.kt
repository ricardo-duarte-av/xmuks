package pt.aguiarvieira.xmuks.core.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import java.io.ByteArrayOutputStream

/**
 * Keeps xmuks' phone contacts (DM partners) in step with the DMs, while the setting is on and we
 * may write contacts; turning it off takes them all away again.
 */
class ContactsSync(
    private val context: Context,
    private val contacts: PhoneContacts,
    private val rooms: RoomListRepository,
    private val preferences: PreferenceStore,
    private val database: XmuksDatabase,
    private val images: ImageLoader,
    private val scope: CoroutineScope,
) {
    /** Photos already fetched, by avatar URL: a rename shouldn't download every avatar again. */
    private val photos = HashMap<String, ByteArray>()

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            val people =
                rooms
                    .directMessages()
                    .map { dms ->
                        dms
                            .mapNotNull { dm ->
                                dm.dmUserId?.let { DmPerson(it, dm.name, dm.avatarUrl, dm.bridgeProtocol) }
                            }.distinctBy { it.userId }
                    }.distinctUntilChanged()
            combine(preferences.value(Prefs.syncContacts), people, database.roomListDao().meta()) { on, list, meta ->
                Triple(on, list, meta?.userId)
            }.debounce(SETTLE_MS).collect { (on, list, me) ->
                if (!allowed()) return@collect
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (on &&
                            me != null
                        ) {
                            contacts.sync(
                                me,
                                list.map { dm -> ContactPerson(dm.userId, dm.name, photo(dm.avatarUrl), dm.network) }
                            )
                        } else {
                            contacts.clear()
                        }
                    }
                }
            }
        }
    }

    private fun allowed() =
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private suspend fun photo(url: String?): ByteArray? {
        url ?: return null
        photos[url]?.let { return it }
        val request =
            ImageRequest
                .Builder(context)
                .data(url)
                .allowHardware(false)
                .size(PHOTO_PX)
                .build()
        val bitmap =
            images
                .execute(request)
                .image
                ?.asDrawable(context.resources)
                ?.toBitmap() ?: return null
        val bytes =
            ByteArrayOutputStream()
                .also {
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        JPEG_QUALITY,
                        it
                    )
                }.toByteArray()
        photos[url] = bytes
        return bytes
    }

    private companion object {
        /** Syncs come in bursts: write once they settle. */
        const val SETTLE_MS = 3_000L
        const val PHOTO_PX = 256
        const val JPEG_QUALITY = 85
    }
}

private data class DmPerson(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val network: String?,
)
