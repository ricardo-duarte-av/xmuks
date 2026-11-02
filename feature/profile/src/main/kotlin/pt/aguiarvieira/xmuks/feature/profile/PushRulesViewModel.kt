package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.data.media.MediaUrls
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.push.Keyword
import pt.aguiarvieira.xmuks.core.data.push.NotifyPreset
import pt.aguiarvieira.xmuks.core.data.push.PushRule
import pt.aguiarvieira.xmuks.core.data.push.PushRuleEditor
import pt.aguiarvieira.xmuks.core.data.push.PushRuleKind
import pt.aguiarvieira.xmuks.core.data.push.PushRuleset
import pt.aguiarvieira.xmuks.core.data.rooms.RoomListRepository
import javax.inject.Inject

/** Who or what a rule is about, as the editor shows it: a name and an avatar, once known. */
@Immutable
data class RuleTarget(
    val id: String,
    val name: String?,
    /** Ready to load (a room's thumbnail), or an `mxc://` URI (a person's) for [ProfileMedia.thumbnail]. */
    val avatar: String?,
)

/** The push rule editor's state and its changes; the keyword screens share it, one room's or the account's. */
@HiltViewModel
class PushRulesViewModel
    @Inject
    constructor(
        private val editor: PushRuleEditor,
        rooms: RoomListRepository,
        private val profiles: ProfileRepository,
        val media: MediaUrls,
    ) : ViewModel() {
        val ruleset: StateFlow<PushRuleset?> = editor.ruleset.stateIn(viewModelScope, WHILE_VISIBLE, null)

        /** Our rooms by ID, for rules about one of them. */
        val rooms: StateFlow<Map<String, RuleTarget>> =
            rooms
                .chats()
                .map { list -> list.associate { it.roomId to RuleTarget(it.roomId, it.name, it.avatarUrl) } }
                .stateIn(viewModelScope, WHILE_VISIBLE, emptyMap())

        /** People's profiles fetched so far, for sender rules. */
        private val _people = MutableStateFlow<Map<String, RuleTarget>>(emptyMap())
        val people: StateFlow<Map<String, RuleTarget>> = _people.asStateFlow()

        val tasks = ProfileTasks(viewModelScope)

        fun setEnabled(
            rule: PushRule,
            enabled: Boolean,
        ) = edit { editor.setEnabled(rule, enabled) }

        fun setPreset(
            rule: PushRule,
            preset: NotifyPreset,
        ) = edit { editor.setActions(rule, preset.actions()) }

        /** The raw editor's [json]: only its actions for the server's own rules, all of it for ours. */
        fun save(
            rule: PushRule,
            json: JsonObject,
        ) = edit {
            if (rule.default) {
                editor.setActions(rule, json["actions"] as? JsonArray ?: JsonArray(emptyList()))
            } else {
                editor.put(rule.kind, rule.ruleId, json)
            }
        }

        fun add(
            kind: PushRuleKind,
            ruleId: String,
            content: JsonObject,
        ) = edit { editor.put(kind, ruleId, content) }

        fun delete(rule: PushRule) = edit { editor.delete(rule.kind, rule.ruleId) }

        fun addKeyword(word: String) = edit { editor.addKeyword(word) }

        fun removeKeyword(keyword: Keyword) = edit { editor.removeKeyword(keyword) }

        fun setKeywordEnabled(
            keyword: Keyword,
            enabled: Boolean,
        ) = edit { editor.setEnabled(PushRule(PushRuleKind.Content, keyword.ruleId), enabled) }

        fun addRoomKeyword(
            roomId: String,
            word: String,
        ) = edit { editor.addRoomKeyword(roomId, word) }

        fun removeRoomKeyword(
            roomId: String,
            word: String,
        ) = edit { editor.removeRoomKeyword(roomId, word) }

        /** Whether one of the account's keywords notifies in [roomId]: off silences it there. */
        fun setKeywordHere(
            roomId: String,
            keyword: Keyword,
            notifies: Boolean,
        ) = edit {
            if (notifies) editor.removeRoomKeyword(roomId, keyword.word) else editor.silenceHere(roomId, keyword.word)
        }

        private val requested = mutableSetOf<String>()

        /** Few at a time: there can be many sender rules. */
        private val fetching = Semaphore(PARALLEL_FETCHES)

        /** A sender rule came on screen: its person's profile is fetched, once. */
        fun needProfile(userId: String) {
            if (!requested.add(userId)) return
            viewModelScope.launch {
                fetching.withPermit { profiles.load(userId) }.onSuccess { profile ->
                    _people.update { it + (userId to RuleTarget(userId, profile.displayName, profile.avatarMxc)) }
                }
            }
        }

        private fun edit(change: suspend () -> String?) =
            tasks.run({
                change()?.let { Result.failure<Unit>(Exception(it)) }
                    ?: Result.success(Unit)
            })

        private companion object {
            const val PARALLEL_FETCHES = 4
            val WHILE_VISIBLE = SharingStarted.WhileSubscribed(5_000)
        }
    }
