package pt.aguiarvieira.xmuks.core.notify

import android.net.Uri
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
            val info = item["info"] as? JsonObject
            if (item.text("msgtype") == "m.image") {
                media(item)?.withKeys(item["file"] as? JsonObject)
            } else {
                thumbnail(item)?.withKeys(info?.get("thumbnail_file") as? JsonObject)
            }
        }
    val count = Galleries.summary(JsonObject(content - "body"))
    return Shown(count, picture, body.takeIf { it.isNotBlank() })
}

/** gomuks keeps no keys for gallery items: an encrypted one's go in its URL, for gomuks to use. */
private fun String.withKeys(file: JsonObject?): String {
    file ?: return this
    val key = (file["key"] as? JsonObject)?.text("k") ?: return this
    val iv = file.text("iv") ?: return this
    val hash = (file["hashes"] as? JsonObject)?.text("sha256") ?: return this

    fun enc(v: String) = Uri.encode(v)
    return "$this&crypto_version=v2&crypto_key=${enc(key)}&crypto_iv=${enc(iv)}&crypto_hash=${enc(hash)}"
}

private val VISUAL = setOf("m.image", "m.video")
