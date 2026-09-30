package pt.aguiarvieira.xmuks.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfoRepository

/** One state event, as shown: its type and key, who set it, and the whole event as JSON. */
data class StateEntry(
    val type: String,
    val stateKey: String,
    val sender: String,
    val json: String,
)

/** The room's state, by type; null while loading. */
data class RoomStateView(
    val groups: Map<String, List<StateEntry>>? = null,
    val error: String? = null,
)

@HiltViewModel(assistedFactory = RoomStateViewModel.Factory::class)
class RoomStateViewModel
    @AssistedInject
    constructor(
        @Assisted roomId: String,
        repository: RoomInfoRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(roomId: String): RoomStateViewModel
        }

        private val _state = MutableStateFlow(RoomStateView())
        val state: StateFlow<RoomStateView> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                repository
                    .stateEvents(roomId)
                    .onSuccess { events ->
                        _state.value =
                            RoomStateView(
                                events
                                    .map(::entryOf)
                                    .sortedWith(compareBy({ it.type }, { it.stateKey }))
                                    .groupBy { it.type },
                            )
                    }.onFailure { _state.value = RoomStateView(emptyMap(), it.message) }
            }
        }

        private fun entryOf(event: JsonObject) =
            StateEntry(
                type = event.text("type"),
                stateKey = event.text("state_key"),
                sender = event.text("sender"),
                json = PRETTY.encodeToString(JsonElement.serializer(), JsonObject(event - GOMUKS_FIELDS)),
            )

        private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.content.orEmpty()

        private companion object {
            /** gomuks' own bookkeeping, not part of the Matrix event. */
            val GOMUKS_FIELDS =
                setOf("rowid", "timeline_rowid", "last_edit_rowid", "local_content", "unread_type", "mem", "pending")

            val PRETTY =
                Json {
                    prettyPrint = true
                    prettyPrintIndent = "  "
                }
        }
    }
