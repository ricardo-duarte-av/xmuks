package pt.aguiarvieira.xmuks.core.data.emoji

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * A custom emoji/sticker pack (MSC2545): our personal one (account data), or one kept in a room's
 * state — the open room's, or one we subscribed to from anywhere.
 */
data class ImagePack(
    val id: String,
    val name: String,
    val iconMxc: String?,
    val emojis: List<PackImage>,
    val stickers: List<PackImage>,
    val source: Source,
) {
    sealed interface Source {
        /** Our own pack (`m.image_pack` / `im.ponies.user_emotes` account data). */
        data object Personal : Source

        /** A pack in a room's state; [subscribed] = in our `m.image_pack.rooms` account data. */
        data class Room(
            val roomId: String,
            val stateKey: String,
            val subscribed: Boolean,
        ) : Source
    }

    companion object {
        const val PERSONAL = "m.image_pack"
        const val PERSONAL_LEGACY = "im.ponies.user_emotes"
        const val ROOM = "m.room.image_pack"
        const val ROOM_LEGACY = "im.ponies.room_emotes"
        const val SUBSCRIPTIONS = "m.image_pack.rooms"
        const val SUBSCRIPTIONS_LEGACY = "im.ponies.emote_rooms"

        /**
         * Parses a pack the way gomuks web does: images without a URL are skipped, an image's
         * `usage` (else the pack's, else both) decides emoji vs sticker, the MSC4389 order field is
         * honoured, and the icon is the pack avatar or its first image. Null if it has no images.
         */
        fun parse(
            content: JsonObject,
            id: String,
            fallbackName: String?,
            source: Source,
        ): ImagePack? {
            val images = content["images"] as? JsonObject ?: return null
            val pack = content["pack"] as? JsonObject
            val packUsage = pack?.usage()
            val (emojis, stickers) = sortImages(images, packUsage)
            if (emojis.isEmpty() && stickers.isEmpty()) return null
            val ordered = { list: List<Pair<Double?, PackImage>> ->
                (
                    if (list.any {
                            it.first != null
                        }
                    ) {
                        list.sortedBy { it.first ?: Double.MAX_VALUE }
                    } else {
                        list
                    }
                ).map { it.second }
            }
            return ImagePack(
                id = id,
                name = pack?.string("display_name")?.takeIf { it.isNotBlank() } ?: fallbackName ?: "Unnamed pack",
                iconMxc =
                    pack?.string("avatar_url")?.takeIf { it.startsWith("mxc://") }
                        ?: (emojis.firstOrNull() ?: stickers.firstOrNull())?.second?.mxc,
                emojis = ordered(emojis),
                stickers = ordered(stickers),
                source = source,
            )
        }

        /** Each image into emoji and/or stickers, with its MSC4389 order. */
        private fun sortImages(
            images: JsonObject,
            packUsage: List<String>?,
        ): Pair<List<Pair<Double?, PackImage>>, List<Pair<Double?, PackImage>>> {
            val emojis = ArrayList<Pair<Double?, PackImage>>()
            val stickers = ArrayList<Pair<Double?, PackImage>>()
            images.forEach { (shortcode, value) ->
                val image = value as? JsonObject ?: return@forEach
                val url = image.string("url")?.takeIf { it.startsWith("mxc://") } ?: return@forEach
                val usage = image.usage() ?: packUsage
                val order = (image["fi.mau.msc4389.order"] as? JsonPrimitive)?.doubleOrNull
                val item =
                    PackImage(shortcode, url, image.string("body") ?: shortcode, image["info"] as? JsonObject)
                if (usage == null || "emoticon" in usage) emojis += order to item
                if (usage == null || "sticker" in usage) stickers += order to item
            }
            return emojis to stickers
        }

        private fun JsonObject.usage(): List<String>? =
            (get("usage") as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }

        private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}

/** One image in a pack: its `:shortcode:`, the image, and what it's called. */
data class PackImage(
    val shortcode: String,
    val mxc: String,
    val body: String,
    val info: JsonObject?,
)
