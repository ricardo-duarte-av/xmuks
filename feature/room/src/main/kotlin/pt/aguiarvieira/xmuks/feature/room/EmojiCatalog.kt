package pt.aguiarvieira.xmuks.feature.room

import android.graphics.Paint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** One Unicode emoji: the character, its group, its name, and what a search matches. */
data class UnicodeEmoji(
    val emoji: String,
    val group: Int,
    val name: String,
    val terms: List<String>,
)

/**
 * The Unicode emoji the picker offers (bundled from emojibase by scripts/update-emoji.py), minus
 * any this device's font can't draw — so nothing shows as a blank box.
 */
object EmojiCatalog {
    class Catalog(
        val groups: List<String>,
        val emoji: List<UnicodeEmoji>,
    )

    /** Loaded once, off the main thread by whoever first asks. */
    val catalog: Catalog by lazy(::load)

    private fun load(): Catalog {
        val text =
            EmojiCatalog::class.java
                .getResourceAsStream("/emoji.json")
                ?.bufferedReader()
                ?.use { it.readText() }
        val root =
            text?.let { GomuksJson.parseToJsonElement(it) as? JsonObject } ?: return Catalog(emptyList(), emptyList())
        val groups = root["groups"]?.jsonArray?.map { (it as JsonPrimitive).content }.orEmpty()
        val paint = Paint()
        val emoji =
            root["emoji"]?.jsonArray.orEmpty().mapNotNull { entry ->
                val e = entry as? JsonArray ?: return@mapNotNull null
                val char = (e[0] as JsonPrimitive).content
                if (!paint.hasGlyph(char)) return@mapNotNull null
                UnicodeEmoji(
                    emoji = char,
                    group = (e[1] as JsonPrimitive).intOrNull ?: 0,
                    name = (e[2] as JsonPrimitive).content,
                    terms = (e[3] as? JsonArray)?.map { (it as JsonPrimitive).content }.orEmpty(),
                )
            }
        return Catalog(groups, emoji)
    }

    /** Emoji whose name or terms contain [query] (name matches first). */
    fun search(query: String): List<UnicodeEmoji> {
        val q =
            query
                .trim()
                .lowercase()
                .removePrefix(":")
                .removeSuffix(":")
        if (q.isEmpty()) return emptyList()
        val all = catalog.emoji
        val byName = all.filter { it.name.lowercase().contains(q) }
        val byTerm = all.filter { e -> e !in byName && e.terms.any { it.contains(q) } }
        return byName + byTerm
    }
}
