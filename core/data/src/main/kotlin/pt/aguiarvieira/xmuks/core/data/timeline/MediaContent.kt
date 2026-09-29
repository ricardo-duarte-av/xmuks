package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonObject

internal fun mediaMessage(
    msgtype: String,
    content: JsonObject,
    body: String,
): MessageContent? {
    val media = media(content) ?: return null
    return when (msgtype) {
        "m.image" -> MessageContent.Image(media, caption(content))
        "m.video" -> MessageContent.Video(media, caption(content))
        "m.audio" -> MessageContent.Audio(media, content.obj("info")?.long("duration"))
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
    )
}

/** Media captions (MSC2530): `body` is the caption when a separate `filename` is given. */
internal fun caption(content: JsonObject): String? {
    val body = content.str("body") ?: return null
    val filename = content.str("filename") ?: return null
    return body.takeIf { it != filename && it.isNotBlank() }
}
