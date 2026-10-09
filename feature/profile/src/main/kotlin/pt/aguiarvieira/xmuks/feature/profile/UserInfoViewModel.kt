package pt.aguiarvieira.xmuks.feature.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.SyncController
import pt.aguiarvieira.xmuks.core.data.contacts.ContactLinks
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.profile.Contacts
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.ProfileFields
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.profile.RoomProfile
import pt.aguiarvieira.xmuks.core.data.profile.RoomProfiles
import pt.aguiarvieira.xmuks.core.data.profile.UserProfile
import pt.aguiarvieira.xmuks.core.data.rooms.RoomSummary
import pt.aguiarvieira.xmuks.core.network.ConnectionState

/** The profile being shown: loading, loaded, or why it couldn't be. */
sealed interface ProfileState {
    data object Loading : ProfileState

    data class Loaded(
        val profile: UserProfile,
    ) : ProfileState

    data class Failed(
        val message: String,
    ) : ProfileState
}

@HiltViewModel(assistedFactory = UserInfoViewModel.Factory::class)
class UserInfoViewModel
    @AssistedInject
    constructor(
        @Assisted("user") val userId: String,
        /** The room it was opened from: their profile there shows too. */
        @Assisted("room") roomId: String?,
        private val profiles: ProfileRepository,
        roomProfiles: RoomProfiles,
        private val contacts: Contacts,
        val media: MediaUrls,
        private val session: SessionRepository,
        sync: SyncController,
        store: CredentialStore,
        @ApplicationContext context: Context,
        links: ContactLinks,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("user") userId: String,
                @Assisted("room") roomId: String?,
            ): UserInfoViewModel
        }

        private val _state = MutableStateFlow<ProfileState>(ProfileState.Loading)
        val state: StateFlow<ProfileState> = _state.asStateFlow()

        /** Opened on ourselves: everything is editable, and the account lives here too. */
        val isMe: StateFlow<Boolean> = profiles.me.map { it == userId }.stateIn(viewModelScope, WHILE_VISIBLE, false)

        val perMessageProfiles: StateFlow<PerMessageProfiles> =
            profiles.perMessageProfiles.stateIn(viewModelScope, WHILE_VISIBLE, PerMessageProfiles.EMPTY)

        val connection: StateFlow<ConnectionState> = sync.state
        val account: String = store.credentials()?.let { "${it.username} · ${it.serverUrl.host}" }.orEmpty()

        /** Whether a change is on its way, and the last one that failed. */
        val tasks = ProfileTasks(viewModelScope)
        private val uploader = ImageUploader(context, profiles)
        val personas = PerMessageProfileActions(profiles, uploader, tasks)

        /** Someone else's: whether we ignore them, and the rooms we share (null: not known). */
        val ignored: StateFlow<Boolean> =
            contacts.ignored.map { userId in it }.stateIn(viewModelScope, WHILE_VISIBLE, false)

        private val _mutualRooms = MutableStateFlow<List<RoomSummary>?>(null)
        val mutualRooms: StateFlow<List<RoomSummary>?> = _mutualRooms.asStateFlow()

        /** Our DM with them, when we're in one: "Message" goes there instead of starting another. */
        private val _directRoom = MutableStateFlow<String?>(null)
        val directRoom: StateFlow<String?> = _directRoom.asStateFlow()

        /** A room to open: the DM, once found or started. */
        private val _openRoom = Channel<String>(Channel.BUFFERED)
        val openRoom: Flow<String> = _openRoom.receiveAsFlow()

        /** Their profile in the room this was opened from, when it's not their global one. */
        private val _roomProfile = MutableStateFlow<RoomProfile?>(null)
        val roomProfile: StateFlow<RoomProfile?> = _roomProfile.asStateFlow()

        /** Their xmuks contact in the phone's Contacts, and linking it to a phone contact. */
        val phoneContact = PhoneContactActions(viewModelScope, links, userId)

        init {
            refresh()
            if (roomId != null) viewModelScope.launch { _roomProfile.value = roomProfiles.of(roomId, userId) }
            viewModelScope.launch {
                if (profiles.me.filterNotNull().first() == userId) return@launch
                _directRoom.value = contacts.directRoom(userId)
                // Unsupported by some servers (MSC2666): the section just doesn't show.
                _mutualRooms.value = contacts.mutualRooms(userId).getOrNull()
            }
        }

        /** Opens our DM with them, starting one first if there's none. */
        fun message() {
            _directRoom.value?.let {
                _openRoom.trySend(it)
                return
            }
            tasks.run({
                contacts.startDirectChat(userId).onSuccess {
                    _directRoom.value = it
                    _openRoom.trySend(it)
                }
            })
        }

        fun setIgnored(ignore: Boolean) = tasks.run({ contacts.setIgnored(userId, ignore) })

        fun refresh() {
            phoneContact.load()
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            profiles
                .load(userId)
                .onSuccess { _state.value = ProfileState.Loaded(it) }
                .onFailure {
                    // A failed refresh keeps what we already show.
                    if (_state.value !is ProfileState.Loaded) _state.value = ProfileState.Failed(it.message.orEmpty())
                }
        }

        fun setDisplayName(name: String) = change { profiles.setText(ProfileFields.DISPLAY_NAME, name.trim()) }

        fun setBio(markdown: String) = change { profiles.setBio(markdown.trim()) }

        fun setStatus(
            text: String,
            emoji: String,
        ) = change {
            val value =
                if (text.isBlank() && emoji.isBlank()) {
                    null
                } else {
                    buildJsonObject {
                        put("text", JsonPrimitive(text.trim()))
                        if (emoji.isNotBlank()) put("emoji", JsonPrimitive(emoji.trim()))
                    }
                }
            // A stable-key status (read first) would otherwise shadow the one we write.
            val stable = (_state.value as? ProfileState.Loaded)?.profile?.raw?.containsKey(ProfileFields.STATUS_STABLE)
            profiles.setField(ProfileFields.STATUS, value).mapCatching {
                if (stable == true) profiles.setField(ProfileFields.STATUS_STABLE, value).getOrThrow()
            }
        }

        /** One set per comma-separated entry, as gomuks web writes them. */
        fun setPronouns(text: String) =
            change {
                val sets =
                    text.split(',').map(String::trim).filter(String::isNotEmpty).map { summary ->
                        buildJsonObject {
                            put("summary", JsonPrimitive(summary))
                            put("language", JsonPrimitive("en"))
                        }
                    }
                profiles.setField(ProfileFields.PRONOUNS, sets.takeIf { it.isNotEmpty() }?.let(::JsonArray))
            }

        fun setTimezone(zone: String?) = change { profiles.setText(ProfileFields.TIMEZONE, zone) }

        /** A new avatar from the photo picker, or none. */
        fun setAvatar(uri: Uri?) = setImage(ProfileFields.AVATAR, uri)

        fun setBanner(uri: Uri?) = setImage(ProfileFields.BANNER, uri)

        private fun setImage(
            field: String,
            uri: Uri?,
        ) = change {
            if (uri == null) {
                profiles.setField(field, null)
            } else {
                uploader.upload(uri).mapCatching { mxc -> profiles.setText(field, mxc).getOrThrow() }
            }
        }

        fun logout() {
            viewModelScope.launch { session.logout() }
        }

        /** Runs one profile change, then reloads so the screen shows what the server now has. */
        private fun change(block: suspend () -> Result<Unit>) = tasks.run(block, then = ::load)

        private companion object {
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
        }
    }

/** Someone's xmuks contact in the phone's Contacts: whether it's linked to a phone contact by hand. */
data class PhoneContactLink(
    val linked: Boolean,
)

/** Someone's xmuks contact (null: none, the option is off) and linking it to a phone contact. */
class PhoneContactActions(
    private val scope: CoroutineScope,
    private val links: ContactLinks,
    private val userId: String,
) {
    private val mutable = MutableStateFlow<PhoneContactLink?>(null)
    val state: StateFlow<PhoneContactLink?> = mutable.asStateFlow()

    fun load() {
        scope.launch(Dispatchers.IO) {
            mutable.value =
                runCatching {
                    if (links.has(
                            userId
                        )
                    ) {
                        PhoneContactLink(links.linkedTo(userId) != null)
                    } else {
                        null
                    }
                }.getOrNull()
        }
    }

    /** Joins their contact to the phone contact picked (or, with null, undoes that). */
    fun link(picked: Uri?) {
        scope.launch(Dispatchers.IO) {
            runCatching { if (picked != null) links.link(userId, picked) else links.unlink(userId) }
            load()
        }
    }
}
