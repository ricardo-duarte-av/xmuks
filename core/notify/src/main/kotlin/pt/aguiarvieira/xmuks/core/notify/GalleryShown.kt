package pt.aguiarvieira.xmuks.core.notify

import kotlinx.serialization.json.JsonObject
import pt.aguiarvieira.xmuks.core.protocol.Galleries

/**
 * An MSC4274 gallery: how many of what, its first picture (unless hidden), and its caption; null
 * when [content] isn't a gallery.
 */
internal fun gallery(
    content: JsonObject,
    body: String,
): Shown? {
    if (content.text("msgtype") !in Galleries.msgtypes) return null
    val first = Galleries.items(content).firstOrNull { it.text("msgtype") in VISUAL }
    val picture =
        first?.takeUnless(::spoiler)?.let { item ->
            if (item.text("msgtype") == "m.image") media(item) else thumbnail(item)
        }
    val count = Galleries.summary(JsonObject(content - "body"))
    return Shown(count, picture, body.takeIf { it.isNotBlank() })
}

private val VISUAL = setOf("m.image", "m.video")
