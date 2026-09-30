package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The link previews bundled in a message: MSC4095's `m.url_previews`, or Beeper's older key. */
internal fun linkPreviewsOf(content: JsonObject): List<LinkPreview> {
    val list = (content["com.beeper.linkpreviews"] ?: content["m.url_previews"]) as? JsonArray ?: return emptyList()
    return list.mapNotNull { (it as? JsonObject)?.let(::linkPreviewOf) }
}

private fun linkPreviewOf(p: JsonObject): LinkPreview? {
    val url = p.str("og:url") ?: p.str("matched_url") ?: return null
    val title = p.str("og:title")?.takeIf { it.isNotBlank() }
    val file = p.obj("beeper:image:encryption")
    val mxc = file?.str("url") ?: p.str("og:image")
    // Nothing to show beyond the link itself, which the message has already.
    if (title == null && mxc == null) return null
    val image =
        mxc?.let {
            Media(
                mxc = it,
                encrypted = file != null,
                mimeType = p.str("og:image:type"),
                width = p.long("og:image:width")?.toInt(),
                height = p.long("og:image:height")?.toInt(),
                size = p.long("matrix:image:size"),
                blurhash = p.str("matrix:image:blurhash"),
                thumbnailMxc = null,
                thumbnailEncrypted = false,
            )
        }
    return LinkPreview(url, title, p.str("og:description")?.takeIf { it.isNotBlank() }, image)
}

/** The http(s) links in [text] a preview could be offered for (as gomuks web finds them). */
fun previewableLinks(text: String): List<String> =
    LINK
        .findAll(text)
        .map { it.value }
        .filterNot { it.startsWith("https://matrix.to") }
        .distinct()
        .toList()

private val LINK = Regex("""\bhttps?://[^\s/_*]+(?:/\S*)?\b""", RegexOption.IGNORE_CASE)

/** A fetched preview as `send_message` bundles it: gomuks' answer, kept whole. */
data class BundledPreview(
    val url: String,
    val json: JsonObject,
    val preview: LinkPreview?,
) {
    companion object {
        fun of(json: JsonObject): BundledPreview =
            BundledPreview((json["matched_url"] as? JsonPrimitive)?.content.orEmpty(), json, linkPreviewOf(json))
    }
}
