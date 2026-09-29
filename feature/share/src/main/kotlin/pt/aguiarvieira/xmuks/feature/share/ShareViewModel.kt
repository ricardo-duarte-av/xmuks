package pt.aguiarvieira.xmuks.feature.share

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.aguiarvieira.xmuks.core.data.media.MediaKind
import pt.aguiarvieira.xmuks.core.data.media.MediaPreparer
import pt.aguiarvieira.xmuks.core.data.media.MediaSender
import pt.aguiarvieira.xmuks.core.data.media.PickedFile
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.data.timeline.DraftStore
import java.io.File
import java.util.UUID

/** One shared file, ready to send: what it is, and what to show of it. */
data class SharedItem(
    val id: String,
    val file: PickedFile,
    /** An image to show for it (the image itself, a video's frame); null for other files. */
    val preview: Uri?,
)

/** What a share brought: files (as content URIs), text, and a room when shared straight to one. */
data class ShareRequest(
    val uris: List<String>,
    val text: String?,
    val roomId: String?,
)

@HiltViewModel(assistedFactory = ShareViewModel.Factory::class)
class ShareViewModel
    @AssistedInject
    constructor(
        @Assisted private val request: ShareRequest,
        @ApplicationContext private val context: Context,
        repository: RoomListRepository,
        private val preparer: MediaPreparer,
        private val sender: MediaSender,
        private val drafts: DraftStore,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(request: ShareRequest): ShareViewModel
        }

        /** The files, once copied in (null while that happens). */
        private val _items = MutableStateFlow<List<SharedItem>?>(null)
        val items: StateFlow<List<SharedItem>?> = _items.asStateFlow()

        /** Names of what couldn't be read. */
        private val _unreadable = MutableStateFlow<List<String>>(emptyList())
        val unreadable: StateFlow<List<String>> = _unreadable.asStateFlow()

        val query = MutableStateFlow("")

        /**
         * The rooms' order when the share opened (most recently active first): kept while the list
         * is up, so a room moving under a finger never gets the tap meant for another.
         */
        private var order: Map<String, Int>? = null

        /** Every room (DMs too), in [order], narrowed by [query]. */
        val rooms: StateFlow<List<RoomSummary>?> =
            combine(repository.chats(), query) { all, q ->
                val fixed = order ?: all.withIndex().associate { (i, room) -> room.roomId to i }.also { order = it }
                val stable = all.sortedBy { fixed[it.roomId] ?: Int.MAX_VALUE }
                val needle = q.trim()
                if (needle.isEmpty()) stable else stable.filter { it.name.contains(needle, ignoreCase = true) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** The room to send to; chosen, or given by a direct share. */
        private val _room = MutableStateFlow(request.roomId)
        val room: StateFlow<String?> = _room.asStateFlow()

        /** The text that came with the share: the first file's caption, or the message itself. */
        val text: String? = request.text?.takeIf { it.isNotBlank() }

        init {
            viewModelScope.launch { _items.value = copyIn(request.uris.map(Uri::parse)) }
        }

        fun choose(roomId: String?) {
            _room.value = roomId
        }

        fun remove(id: String) = _items.update { list -> list?.filterNot { it.id == id } }

        /**
         * Sends every file with its caption (in order, each its own message), or — with no files —
         * puts the text in the room's composer. The result is the room, to open.
         */
        suspend fun send(captions: Map<String, String>): String? {
            val roomId = _room.value ?: return null
            val files = _items.value.orEmpty()
            if (files.isEmpty()) {
                text?.let { drafts.save(roomId, it) }
                return roomId
            }
            val encrypted = rooms.value?.firstOrNull { it.roomId == roomId }?.encrypted == true
            files.forEach { item ->
                runCatching { preparer.prepare(item.file) }.onSuccess { prepared ->
                    sender.send(roomId, prepared, captions[item.id].orEmpty().trim(), null, encrypted)
                }
            }
            return roomId
        }

        /**
         * Copies each shared file into our cache first: the sending app's permission to read it can
         * end (with its task) before the upload does. Names, types and sizes come from the original.
         */
        private suspend fun copyIn(uris: List<Uri>): List<SharedItem> =
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, SHARED).apply { mkdirs() }
                dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > KEEP_MS }?.forEach {
                    it
                        .deleteRecursively()
                }
                uris.mapNotNull { uri ->
                    val id = UUID.randomUUID().toString()
                    val picked = runCatching { preparer.inspect(uri) }.getOrNull()
                    val copy =
                        picked?.let {
                            runCatching {
                                val target = File(File(dir, id).apply { mkdirs() }, safeName(it.name))
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    target.outputStream().use { input.copyTo(it) }
                                } ?: error("No stream")
                                target
                            }.getOrNull()
                        }
                    if (picked == null || copy == null) {
                        _unreadable.update { it + (picked?.name ?: uri.lastPathSegment ?: "?") }
                        return@mapNotNull null
                    }
                    val file = picked.copy(uri = Uri.fromFile(copy), size = copy.length())
                    val preview =
                        when (file.kind) {
                            MediaKind.Image -> file.uri
                            MediaKind.Video -> preparer.videoPreview(file)?.let(Uri::fromFile)
                            else -> null
                        }
                    SharedItem(id, file, preview)
                }
            }

        private fun safeName(name: String) = name.replace('/', '_').ifBlank { "file" }

        private companion object {
            const val SHARED = "shared"
            const val KEEP_MS = 24L * 60 * 60 * 1000
        }
    }
