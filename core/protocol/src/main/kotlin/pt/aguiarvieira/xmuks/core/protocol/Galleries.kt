package pt.aguiarvieira.xmuks.core.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * MSC4274 inline media galleries: one `m.room.message` carrying several pictures, videos, sounds or
 * files in `itemtypes` — each shaped like that msgtype's content, with `itemtype` for `msgtype`.
 * The gallery's own `body` / `formatted_body` are its caption.
 */
object Galleries {
    const val MSGTYPE = "m.gallery"

    /** What clients (Filament, Element) send while the MSC is unstable. */
    const val UNSTABLE_MSGTYPE = "dm.filament.gallery"

    val msgtypes = setOf(MSGTYPE, UNSTABLE_MSGTYPE)

    /** The items' contents, in order, each with its `itemtype` as `msgtype`; unknown kinds left out. */
    fun items(content: JsonObject): List<JsonObject> =
        (content["itemtypes"] as? JsonArray)
            .orEmpty()
            .mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val type = (obj["itemtype"] as? JsonPrimitive)?.contentOrNull
                if (type !in ITEM_TYPES) return@mapNotNull null
                JsonObject(obj + ("msgtype" to JsonPrimitive(type)))
            }

    /** "🖼️ 6 photos" (or videos, or items), or the caption when it has one: for one-line previews. */
    fun summary(content: JsonObject): String {
        val caption = (content["body"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (caption != null) return "🖼️ $caption"
        val types = items(content).map { (it["msgtype"] as JsonPrimitive).content }
        val what =
            when {
                types.isNotEmpty() && types.all { it == "m.image" } -> if (types.size == 1) "photo" else "photos"
                types.isNotEmpty() && types.all { it == "m.video" } -> if (types.size == 1) "video" else "videos"
                else -> if (types.size == 1) "item" else "items"
            }
        return "🖼️ ${types.size} $what"
    }

    private val ITEM_TYPES = setOf("m.image", "m.video", "m.audio", "m.file")
}
