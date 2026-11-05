package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.emoji.PackImage
import pt.aguiarvieira.xmuks.core.data.emoji.closedShortcodeAt
import pt.aguiarvieira.xmuks.core.data.emoji.matchingShortcode
import pt.aguiarvieira.xmuks.core.data.emoji.shortcodeQueryAt
import pt.aguiarvieira.xmuks.core.data.rooms.MentionTarget
import pt.aguiarvieira.xmuks.core.data.rooms.MentionTargets
import pt.aguiarvieira.xmuks.core.data.rooms.matching
import pt.aguiarvieira.xmuks.core.data.rooms.mentionQueryAt

/** One emoji a `:shortcode` being typed could become, and the shortcode that matched. */
class EmojiHint(
    val picked: Picked,
    val shortcode: String,
    /** The Unicode emoji's name; none for a custom one. */
    val name: String? = null,
)

/**
 * Completing mentions and emoji as they're typed: `@` offers the room's members (as they're named
 * here), `#` the rooms we're in, `:` emoji by shortcode (our custom ones first). Picking one puts it
 * in place of what was typed, and so does closing a shortcode with `:` (`:memo:` → 📝). The
 * composer has one, and so does a media caption's box.
 */
class ComposerMentions(
    private val scope: CoroutineScope,
    private val draft: TextFieldState,
    private val roomId: String,
    private val targets: MentionTargets,
    /** Our custom emoji usable here; they stay `:shortcode:` in the box, expanded when sent. */
    private val customEmoji: StateFlow<List<PackImage>> = MutableStateFlow(emptyList()),
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

    private val shortcodeQuery =
        snapshotFlow {
            shortcodeQueryAt(
                draft.text.toString(),
                draft.selection.end.takeIf { draft.selection.collapsed } ?: -1
            )
        }.distinctUntilChanged()

    val emoji: StateFlow<List<EmojiHint>> =
        combine(shortcodeQuery, customEmoji) { q, custom ->
            if (q == null) return@combine emptyList()
            // A shortcode two packs share is the first one's, as when it's sent.
            val candidates = custom.distinctBy { it.shortcode }.map { Picked.Custom(it) } + unicodeCandidates
            candidates.matchingShortcode(q.text, { shortcodesOf(it) }).map { (picked, code) ->
                EmojiHint(picked, code, (picked as? Picked.Unicode)?.let { unicodeByChar[it.emoji]?.name })
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    init {
        scope.launch { swapClosedShortcodes() }
    }

    /**
     * A `:` typed right after a shortcode (`:memo` → `:memo:`) swaps it for its Unicode emoji. Only
     * when typed: text put in whole (a draft restored, an edit, a paste) is left as it is, and so is
     * one of our custom emoji's, which is expanded when sent.
     */
    private suspend fun swapClosedShortcodes() {
        var before = draft.text.toString()
        snapshotFlow { draft.text.toString() to draft.selection }.collect { (text, selection) ->
            val previous = before
            before = text
            if (!selection.collapsed || !typedColon(previous, text, selection.end)) return@collect
            val closed = closedShortcodeAt(text, selection.end) ?: return@collect
            if (customEmoji.value.any { it.shortcode == closed.text }) return@collect
            val glyph = EmojiCatalog.byShortcode(closed.text)?.emoji ?: return@collect
            draft.edit {
                replace(closed.start, closed.end, glyph)
                this.selection = TextRange(closed.start + glyph.length)
            }
        }
    }

    /** [hint]'s emoji in place of the shortcode being typed: Unicode as is, a custom one as `:shortcode:`. */
    fun pickEmoji(hint: EmojiHint) {
        val q = shortcodeQueryAt(draft.text.toString(), draft.selection.end) ?: return
        val insert =
            when (val picked = hint.picked) {
                is Picked.Unicode -> picked.emoji
                is Picked.Custom -> ":${picked.image.shortcode}:"
            }
        draft.edit {
            replace(q.start, q.end, insert)
            selection = TextRange(q.start + insert.length)
        }
    }

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

        /** Every Unicode emoji with a shortcode, in the picker's order. */
        val unicodeCandidates: List<Picked> by lazy {
            EmojiCatalog.catalog.emoji
                .filter { it.shortcodes.isNotEmpty() }
                .map { Picked.Unicode(it.emoji) }
        }

        val unicodeByChar: Map<String, UnicodeEmoji> by lazy { EmojiCatalog.catalog.emoji.associateBy { it.emoji } }

        fun shortcodesOf(picked: Picked): List<String> =
            when (picked) {
                is Picked.Unicode -> unicodeByChar[picked.emoji]?.shortcodes.orEmpty()
                is Picked.Custom -> listOf(picked.image.shortcode)
            }
    }
}

/** [text] is [previous] with one `:` typed just before [cursor]. */
private fun typedColon(
    previous: String,
    text: String,
    cursor: Int,
): Boolean {
    if (cursor <= 0 || text.length != previous.length + 1 || text[cursor - 1] != ':') return false
    return text.regionMatches(0, previous, 0, cursor - 1) &&
        text.regionMatches(cursor, previous, cursor - 1, text.length - cursor)
}
