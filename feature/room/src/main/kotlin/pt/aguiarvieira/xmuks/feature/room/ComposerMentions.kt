package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.rooms.MentionTarget
import pt.aguiarvieira.xmuks.core.data.rooms.MentionTargets
import pt.aguiarvieira.xmuks.core.data.rooms.matching
import pt.aguiarvieira.xmuks.core.data.rooms.mentionQueryAt

/**
 * Completing mentions as they're typed: `@` offers the room's members (as they're named here), `#`
 * the rooms we're in. Picking one puts its link in place of what was typed.
 */
internal class ComposerMentions(
    private val scope: CoroutineScope,
    private val draft: TextFieldState,
    private val roomId: String,
    private val targets: MentionTargets,
) {
    /** Fetched the first time someone's mentioned: the full list can be long. */
    private val members = MutableStateFlow<List<MentionTarget>?>(null)
    private var fetching = false

    private val query =
        snapshotFlow {
            mentionQueryAt(
                draft.text.toString(),
                draft.selection.end.takeIf { draft.selection.collapsed } ?: -1
            )
        }.distinctUntilChanged()

    val suggestions: StateFlow<List<MentionTarget>> =
        combine(query, members, targets.rooms()) { q, people, rooms ->
            when {
                q == null -> emptyList()
                q.room -> rooms.matching(q.text)
                else -> people?.matching(q.text).also { if (people == null) fetchMembers() }.orEmpty()
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    private fun fetchMembers() {
        if (fetching) return
        fetching = true
        scope.launch { members.value = targets.members(roomId) }
    }

    /** [target]'s link in place of the mention being typed. */
    fun pick(target: MentionTarget) {
        val q = mentionQueryAt(draft.text.toString(), draft.selection.end) ?: return
        val insert = target.link + " "
        draft.edit {
            replace(q.start, q.end, insert)
            selection = TextRange(q.start + insert.length)
        }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
