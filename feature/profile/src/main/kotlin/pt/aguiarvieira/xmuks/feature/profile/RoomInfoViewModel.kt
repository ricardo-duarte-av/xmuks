package pt.aguiarvieira.xmuks.feature.profile

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.push.RoomPushRules
import pt.aguiarvieira.xmuks.core.data.roominfo.MembershipAction
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomDirectoryRepository
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import pt.aguiarvieira.xmuks.core.notify.roomNotificationSettings

/** The room being shown: loading, loaded, or why it couldn't be. */
sealed interface RoomInfoState {
    data object Loading : RoomInfoState

    data class Loaded(
        val info: RoomInfo,
    ) : RoomInfoState

    data class Failed(
        val message: String,
    ) : RoomInfoState
}

@HiltViewModel(assistedFactory = RoomInfoViewModel.Factory::class)
class RoomInfoViewModel
    @AssistedInject
    constructor(
        @Assisted val roomId: String,
        private val rooms: RoomInfoRepository,
        directoryRepository: RoomDirectoryRepository,
        profiles: ProfileRepository,
        pushRules: RoomPushRules,
        val media: MediaUrls,
        @ApplicationContext private val context: Context,
        roomList: RoomListRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String): RoomInfoViewModel
        }

        private val _state = MutableStateFlow<RoomInfoState>(RoomInfoState.Loading)
        val state: StateFlow<RoomInfoState> = _state.asStateFlow()

        val me: StateFlow<String?> = rooms.me.stateIn(viewModelScope, WHILE_VISIBLE, null)

        val tasks = ProfileTasks(viewModelScope)
        private val uploader = ImageUploader(context, profiles)

        /** Our per-message profiles for this room only (room account data). */
        val personas = PerMessageProfileActions(profiles, uploader, tasks, roomId)
        val roomPersonas: StateFlow<PerMessageProfiles> =
            profiles.perMessageProfiles(roomId).stateIn(viewModelScope, WHILE_VISIBLE, PerMessageProfiles.EMPTY)

        val notifications = RoomNotificationActions(viewModelScope, roomId, pushRules, WHILE_VISIBLE)

        /** The room as the list has it: its name, and whether it's a DM (which notification channel it's under). */
        private val summary = roomList.room(roomId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** Android's notification settings for this room alone, on this phone (sound, vibration…). */
        fun phoneNotificationSettings(): Intent {
            val room = summary.value
            return roomNotificationSettings(context, roomId, room?.name ?: roomId, room?.isDirect == true)
        }

        /** Whether the room is listed in our server's directory (not for DMs). */
        val directory =
            RoomDirectoryActions(
                viewModelScope,
                roomId,
                directoryRepository,
                tasks,
                rooms.me,
                summary.map { it?.isDirect },
                WHILE_VISIBLE,
            )

        /** Set once we've left: the screen goes back. */
        private val _left = MutableStateFlow(false)
        val left: StateFlow<Boolean> = _left.asStateFlow()

        init {
            // Loaded now, and again whenever the room's state changes (ours or anyone's edits).
            viewModelScope.launch { rooms.stateChanges(roomId).collectLatest { load() } }
        }

        fun refresh() {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            rooms
                .load(roomId)
                .onSuccess { _state.value = RoomInfoState.Loaded(it) }
                .onFailure {
                    if (_state.value !is RoomInfoState.Loaded) _state.value = RoomInfoState.Failed(it.message.orEmpty())
                }
        }

        fun setName(name: String) = setState("m.room.name", text("name", name.trim()))

        fun setTopic(topic: String) = setState("m.room.topic", text("topic", topic.trim()))

        fun setAvatar(uri: Uri?) =
            tasks.run({
                val url = if (uri == null) Result.success(null) else uploader.upload(uri)
                url.mapCatching { mxc ->
                    val content = buildJsonObject { mxc?.let { put("url", JsonPrimitive(it)) } }
                    rooms.setState(roomId, "m.room.avatar", content).getOrThrow()
                }
            })

        fun setJoinRule(rule: String) = setState("m.room.join_rules", text("join_rule", rule))

        fun setHistoryVisibility(value: String) =
            setState("m.room.history_visibility", text("history_visibility", value))

        fun enableEncryption() = setState("m.room.encryption", text("algorithm", MEGOLM))

        fun setLevel(
            userId: String,
            level: Long,
        ) = editLevels { it.withUser(userId, level) }

        /** Sends the power levels as [change] makes them from the current ones. */
        fun editLevels(change: (PowerLevels) -> JsonObject) {
            val levels = (_state.value as? RoomInfoState.Loaded)?.info?.powerLevels ?: return
            setState(PowerLevels.TYPE, change(levels))
        }

        fun membership(
            userId: String,
            action: MembershipAction,
            reason: String? = null,
        ) = tasks.run({ rooms.setMembership(roomId, userId, action, reason) }, then = ::load)

        fun leave(reason: String?) =
            tasks.run({
                rooms.leave(roomId, reason).onSuccess { _left.value = true }
            })

        private fun setState(
            type: String,
            content: JsonObject,
        ) = tasks.run({ rooms.setState(roomId, type, content) })

        private fun text(
            key: String,
            value: String,
        ) = buildJsonObject { put(key, JsonPrimitive(value)) }

        private companion object {
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
            const val MEGOLM = "m.megolm.v1.aes-sha2"
        }
    }
