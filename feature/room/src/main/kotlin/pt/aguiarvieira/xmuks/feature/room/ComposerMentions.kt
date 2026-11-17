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
import pt.aguiarvieira.xmuks.core.data.emoji.emojiForSmiley
import pt.aguiarvieira.xmuks.core.data.emoji.isInstantSmiley
import pt.aguiarvieira.xmuks.core.data.emoji.matchingShortcode
import pt.aguiarvieira.xmuks.core.data.emoji.shortcodeQueryAt
import pt.aguiarvieira.xmuks.core.data.emoji.smileyEndingAt
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
    /** The skin tone Unicode emoji take when completed ([EmojiCatalog.withTone]). */
    private val skinTone: StateFlow<String?> = MutableStateFlow(null),
    /** Text smileys typed become emoji (`:)` → 🙂), xmuks' setting. */
    private val smileys: StateFlow<Boolean> = MutableStateFlow(false),
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
        combine(shortcodeQuery, customEmoji, skinTone) { q, custom, tone ->
            if (q == null) return@combine emptyList()
            // A shortcode two packs share is the first one's, as when it's sent.
            val candidates = custom.distinctBy { it.shortcode }.map { Picked.Custom(it) } + unicodeCandidates
            candidates.matchingShortcode(q.text, { shortcodesOf(it) }).map { (picked, code) ->
                when (picked) {
                    is Picked.Unicode -> {
                        val name = unicodeByChar[picked.emoji]?.name
                        EmojiHint(Picked.Unicode(EmojiCatalog.withTone(picked.emoji, tone)), code, name)
                    }

                    is Picked.Custom -> {
                        EmojiHint(picked, code, null)
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    /**
     * The last smiley swapped. One backspace right after it (the emoji, or the space typed after it)
     * puts the text back.
     */
    private var swapped: SmileySwap? = null

    /** A smiley put back that way: left alone when it would be swapped again. */
    private var kept: Pair<Int, String>? = null

    init {
        scope.launch { swapTyped() }
    }

    /**
     * Swaps what was just typed. A `:` typed right after a shortcode (`:memo` → `:memo:`) becomes its
     * Unicode emoji. With that setting on, smileys become emoji too: one ending in punctuation (`:)`)
     * at once, one ending in a letter or digit (`:D`, `<3`) when a space or new line follows it, and a
     * character that makes a longer smiley (`-_-` then `;`) swaps the emoji for that one's. This only
     * happens when typed: text put in whole (a draft restored, an edit, a paste) is left as it is, and
     * so is one of our custom emoji's shortcodes, which are expanded when sent.
     */
    private suspend fun swapTyped() {
        var before = draft.text.toString()
        snapshotFlow { draft.text.toString() to draft.selection }.collect { (text, selection) ->
            val previous = before
            before = text
            // Gone (edited away, or sent): the same smiley typed again is swapped again.
            val keep = kept
            if (keep != null && !text.startsWith(keep.second, keep.first)) kept = null
            val last = swapped
            if (last != null && text != last.after) {
                swapped = null
                if (selection.collapsed && last.undoneBy(text, selection.end)) {
                    undo(last, selection.end)
                    return@collect
                }
            }
            if (!selection.collapsed) return@collect
            val cursor = selection.end
            val typed = typedAt(previous, text, cursor) ?: return@collect
            when {
                typed == ':' -> swapShortcode(text, cursor)
                !smileys.value -> Unit
                typed == ' ' || typed == '\n' -> swapSmiley(text, cursor - 1)
                last != null && previous == last.after && cursor == last.end + 1 -> extendSmiley(last, typed, cursor)
                !typed.isLetterOrDigit() -> swapSmiley(text, cursor, cursor, instant = true)
            }
        }
    }

    private fun swapShortcode(
        text: String,
        cursor: Int,
    ) {
        val closed = closedShortcodeAt(text, cursor) ?: return
        if (customEmoji.value.any { it.shortcode == closed.text }) return
        val emoji = EmojiCatalog.byShortcode(closed.text)?.emoji ?: return
        val glyph = EmojiCatalog.withTone(emoji, skinTone.value)
        draft.edit {
            replace(closed.start, closed.end, glyph)
            selection = TextRange(closed.start + glyph.length)
        }
    }

    /**
     * The smiley ending at [end] as its emoji; the cursor, at [cursor] past it, keeps its place. An
     * [instant] swap is only for those [isInstantSmiley] says can't go on.
     */
    private fun swapSmiley(
        text: String,
        end: Int,
        cursor: Int = end + 1,
        instant: Boolean = false,
    ) {
        val smiley = smileyEndingAt(text, end) ?: return
        val original = text.substring(smiley.start, smiley.end)
        if (instant && !isInstantSmiley(original)) return
        if (kept == smiley.start to original) return
        kept = null
        draft.edit {
            replace(smiley.start, smiley.end, smiley.emoji)
            selection = TextRange(cursor + smiley.emoji.length - original.length)
        }
        swapped = SmileySwap(smiley.start, smiley.emoji, original, draft.text.toString())
    }

    /** [typed] right after [last]'s emoji makes a longer smiley (`-_-` + `;`): that one's emoji instead. */
    private fun extendSmiley(
        last: SmileySwap,
        typed: Char,
        cursor: Int,
    ) {
        val original = last.original + typed
        val emoji = emojiForSmiley(original) ?: return
        draft.edit {
            replace(last.start, cursor, emoji)
            selection = TextRange(last.start + emoji.length)
        }
        swapped = SmileySwap(last.start, emoji, original, draft.text.toString())
    }

    /** [swap]'s text back in place of its emoji, what's left of it before [cursor]. */
    private fun undo(
        swap: SmileySwap,
        cursor: Int,
    ) {
        draft.edit {
            replace(swap.start, cursor, swap.original)
            selection = TextRange(swap.start + swap.original.length)
        }
        kept = swap.start to swap.original
    }

    /**
     * A smiley ending the message becomes its emoji as it's sent (`ok :)`, sent with no space typed
     * after it), when that setting is on.
     */
    fun swapLastSmiley() {
        if (!smileys.value) return
        val text = draft.text.toString()
        val end = text.trimEnd().length
        swapSmiley(text, end, cursor = end)
        swapped = null
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

/** The one character typed just before [cursor], when [text] is [previous] with only that added. */
private fun typedAt(
    previous: String,
    text: String,
    cursor: Int,
): Char? {
    if (cursor <= 0 || text.length != previous.length + 1) return null
    val same =
        text.regionMatches(0, previous, 0, cursor - 1) &&
            text.regionMatches(cursor, previous, cursor - 1, text.length - cursor)
    return if (same) text[cursor - 1] else null
}

/** A smiley swapped for [emoji] at [start], leaving the box as [after]. */
private class SmileySwap(
    val start: Int,
    val emoji: String,
    val original: String,
    val after: String,
) {
    /** Where the emoji ends. */
    val end: Int get() = start + emoji.length

    /**
     * [text] is [after] less one backspace's worth right after the swap: the emoji (all of it, or
     * its end) with the cursor where it was, or the space typed after it with the cursor at its end.
     */
    fun undoneBy(
        text: String,
        cursor: Int,
    ): Boolean {
        if (cursor !in start..end) return false
        val removed =
            if (cursor == end) {
                if (after.getOrNull(end)?.isWhitespace() != true) return false
                end + 1
            } else {
                end
            }
        return text.length == after.length - (removed - cursor) &&
            text.regionMatches(0, after, 0, cursor) &&
            text.regionMatches(cursor, after, removed, text.length - cursor)
    }
}
