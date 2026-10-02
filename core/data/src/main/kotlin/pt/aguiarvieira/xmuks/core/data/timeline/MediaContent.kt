package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal fun mediaMessage(
    msgtype: String,
    content: JsonObject,
    body: String,
): MessageContent? {
    val media = media(content) ?: return null
    return when (msgtype) {
        "m.image" -> MessageContent.Image(media, caption(content))
        "m.video" -> MessageContent.Video(media, caption(content))
        "m.audio" -> audioMessage(media, content, body)
        else -> MessageContent.File(media, content.str("filename") ?: body)
    }
}

/** Unencrypted media has `url`; encrypted media has `file.url` (gomuks decrypts on download). */
internal fun media(content: JsonObject): Media? {
    val file = content.obj("file")
    val mxc = content.str("url") ?: file?.str("url") ?: return null
    val info = content.obj("info")
    val thumbFile = info?.obj("thumbnail_file")
    return Media(
        mxc = mxc,
        encrypted = file != null,
        mimeType = info?.str("mimetype"),
        width = info?.long("w")?.toInt(),
        height = info?.long("h")?.toInt(),
        size = info?.long("size"),
        blurhash = info?.str("xyz.amorgan.blurhash") ?: content.str("xyz.amorgan.blurhash"),
        thumbnailMxc = info?.str("thumbnail_url") ?: thumbFile?.str("url"),
        thumbnailEncrypted = thumbFile != null,
        name = content.str("filename") ?: content.str("body"),
        spoiler = content.bool(SPOILER) == true || content.bool(STABLE_SPOILER) == true,
        spoilerReason =
            (
                content.str(
                    "$SPOILER.reason"
                ) ?: content.str("$STABLE_SPOILER.reason")
            )?.takeIf { it.isNotBlank() },
    )
}

/** MSC4193's media spoiler: its unstable key (what clients send today) and the stable one. */
const val SPOILER = "page.codeberg.everypizza.msc4193.spoiler"
private const val STABLE_SPOILER = "m.spoiler"

/** Media captions (MSC2530): `body` is the caption when a separate `filename` is given. */
internal fun caption(content: JsonObject): String? {
    val body = content.str("body") ?: return null
    val filename = content.str("filename") ?: return null
    return body.takeIf { it != filename && it.isNotBlank() }
}

/** Audio, with MSC1767's duration and waveform and MSC3245's voice flag when present. */
private fun audioMessage(
    media: Media,
    content: JsonObject,
    body: String,
): MessageContent.Audio {
    val extensible = content.obj("org.matrix.msc1767.audio") ?: content.obj("m.audio")
    val samples =
        (extensible?.get("waveform") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            ?.takeIf { it.isNotEmpty() }
    // Senders use different ranges (MSC3246 says 0..1024, gomuks writes 0..256): scale to the loudest.
    val waveform =
        samples?.let { values ->
            val loudest = values.max().coerceAtLeast(1)
            values.map { it.toFloat() / loudest }
        }
    return MessageContent.Audio(
        media = media,
        durationMs = content.obj("info")?.long("duration") ?: extensible?.long("duration"),
        waveform = waveform,
        voice = content.containsKey("org.matrix.msc3245.voice") || content.containsKey("m.voice"),
        name = content.str("filename") ?: body.takeIf { it.isNotBlank() },
    )
}
