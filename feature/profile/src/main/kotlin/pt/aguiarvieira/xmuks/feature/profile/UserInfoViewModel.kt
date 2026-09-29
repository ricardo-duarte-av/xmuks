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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.auth.CredentialStore
import pt.aguiarvieira.xmuks.core.data.auth.SessionRepository
import pt.aguiarvieira.xmuks.core.data.connection.SyncController
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.ProfileFields
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.profile.UserProfile
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
        @Assisted val userId: String,
        private val profiles: ProfileRepository,
        val media: MediaUrls,
        private val session: SessionRepository,
        sync: SyncController,
        store: CredentialStore,
        @ApplicationContext context: Context,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(userId: String): UserInfoViewModel
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

        init {
            refresh()
        }

        fun refresh() {
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
