package pt.aguiarvieira.xmuks.feature.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.emoji.ImagePack
import pt.aguiarvieira.xmuks.core.data.emoji.PackImage
import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.data.timeline.Reaction
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyTarget
import pt.aguiarvieira.xmuks.core.data.timeline.RoomFeatures
import pt.aguiarvieira.xmuks.core.data.timeline.RoomSession
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** Emoji (reactions, the composer) or stickers. */
enum class PickerMode { Emoji, Sticker }

/** Something picked in the emoji or sticker picker. */
sealed interface Picked {
    data class Unicode(
        val emoji: String,
    ) : Picked

    data class Custom(
        val image: PackImage,
    ) : Picked

    /** What a reaction's key is: the emoji itself, or the custom image's `mxc://`. */
    val key: String
        get() =
            when (this) {
                is Unicode -> emoji
                is Custom -> image.mxc
            }
}

/** Reacting, stickers, recent emoji and pack subscriptions, for the room screen. */
class EmojiActions(
    private val scope: CoroutineScope,
    private val session: RoomSession,
    started: SharingStarted,
    /** What the bridged network here allows; null when nothing is limited. */
    private val features: () -> RoomFeatures? = { null },
    /** A reaction the network won't take, not sent: said in a toast. */
    private val onLimited: (Limit) -> Unit = {},
    /** Where a sticker goes: a reply, or into the thread shown. */
    private val replyTo: () -> ReplyTarget? = { null },
) {
    val packs: StateFlow<List<ImagePack>> = session.emoji.packs.stateIn(scope, started, emptyList())
    val recent: StateFlow<List<String>> = session.emoji.recent.stateIn(scope, started, emptyList())

    init {
        // Checking ~1,900 emoji against the font takes a moment: do it before the picker opens.
        scope.launch(Dispatchers.Default) { EmojiCatalog.catalog }
    }

    /** Adds [picked] as our reaction to [message], or takes it back if we already have it. */
    fun react(
        message: TimelineItem.Message,
        picked: Picked,
    ) {
        val existing = message.reactions.firstOrNull { it.key == picked.key }
        if (existing?.mine != true && refused(picked.key, picked is Picked.Custom)) return
        val shortcode = (picked as? Picked.Custom)?.image?.shortcode
        val replacing = makingWay(message, picked.key, existing)
        scope.launch { session.emoji.toggleReaction(message.eventId, picked.key, existing, shortcode, replacing) }
    }

    /** Tapping a reaction under a message: join in, or take ours back. */
    fun toggle(
        message: TimelineItem.Message,
        key: String,
    ) {
        val existing = message.reactions.firstOrNull { it.key == key }
        // Taking ours back is always fine; joining in is a new reaction.
        if (existing?.mine != true && refused(key, key.startsWith("mxc://"))) return
        val replacing = makingWay(message, key, existing)
        scope.launch { session.emoji.toggleReaction(message.eventId, key, existing, replacing = replacing) }
    }

    /** Whether the network here won't take [key] as a reaction: if so, it's said, not sent. */
    private fun refused(
        key: String,
        custom: Boolean,
    ): Boolean {
        val limit = features()?.reactionBlock(key, custom) ?: return false
        onLimited(limit)
        return true
    }

    /**
     * Our reactions to [message] that go to make room for [key], where the network allows only so
     * many each (one, on WhatsApp): it replaces them there, so we do here too.
     */
    private fun makingWay(
        message: TimelineItem.Message,
        key: String,
        existing: Reaction?,
    ): List<Reaction> {
        val max = features()?.reactionCount ?: return emptyList()
        if (existing?.mine == true) return emptyList()
        val ours = message.reactions.filter { it.mine && it.key != key }
        return ours.take((ours.size - max + 1).coerceAtLeast(0))
    }

    /**
     * `:shortcode:` of one of our custom emoji → gomuks' markdown for an inline emoticon. The
     * composer shows the short form; this is what's sent. Earlier packs win a shared shortcode
     * (personal, then this room's, then subscribed — the picker's order).
     */
    fun expandShortcodes(text: String): String {
        if (!text.contains(':')) return text
        val byShortcode = HashMap<String, PackImage>()
        packs.value.forEach { pack -> pack.emojis.forEach { byShortcode.putIfAbsent(it.shortcode, it) } }
        return SHORTCODE.replace(text) { match ->
            val image = byShortcode[match.groupValues[1]] ?: return@replace match.value
            "![:${image.shortcode}:](${image.mxc} \"Emoji: :${image.shortcode}:\")"
        }
    }

    fun sendSticker(image: PackImage) {
        val replyTo = replyTo()
        scope.launch { session.writer.sendSticker(image, replyTo) }
    }

    /** An emoji inserted into the composer counts as used, like a reaction. */
    fun used(picked: Picked) {
        scope.launch { session.emoji.bumpRecent(picked.key) }
    }

    fun setSubscribed(
        pack: ImagePack,
        subscribe: Boolean,
    ) {
        val source = pack.source as? ImagePack.Source.Room ?: return
        scope.launch { session.emoji.setSubscribed(source, subscribe) }
    }
}

private val SHORTCODE = Regex(""":([^\s:]+):""")
