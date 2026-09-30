package pt.aguiarvieira.xmuks.core.push

import android.text.SpannableStringBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import pt.aguiarvieira.xmuks.core.protocol.Event

/** What a notification shows for one message: its text (styled where it can be) and a picture. */
internal data class Shown(
    /** The message; for a picture, what it is ("📷 Photo"): what Android Auto shows and reads out. */
    val text: CharSequence,
    /** A gomuks media path (`_gomuks/media/…`), relative to the server; null for none. */
    val picture: String? = null,
    /** A picture's or video's caption: a line of its own after it. */
    val caption: CharSequence? = null,
)

/** What the push alone says: its text, and its picture if it has one. */
internal fun shownOf(push: PushMessage): Shown =
    if (push.image == null) {
        Shown(push.text)
    } else {
        Shown(PHOTO, push.image, push.text.takeUnless { it.isBlank() || it.startsWith("Sent ") })
    }

/**
 * The notification's view of [event] (fetched with `get_event`): formatted text as far as a
 * notification can show it (bold, italic, strikethrough, underline, monospace), a video's
 * thumbnail as its picture, and a line saying what audio, files, locations and polls are.
 * [push] is what gomuks pushed, used where the event says no more (or can't be read).
 */
internal fun shownOf(
    event: Event,
    push: PushMessage,
): Shown {
    val content = event.effectiveContent
    val body = content.text("body").orEmpty()
    val caption = body.takeIf { it.isNotBlank() && content.text("filename")?.let { f -> f != body } == true }
    val fallback = shownOf(push)
    if (event.redactedBy != null || (event.type == "m.room.encrypted" && event.decrypted == null)) return fallback
    // Spoilers stay hidden, as gomuks' own preview hides them.
    if (content.text("formatted_body")?.contains("data-mx-spoiler") == true) return fallback
    return byType(event, push, content, body, caption) ?: fallback
}

/** What each kind of message shows; null for kinds with nothing better than the push's text. */
private fun byType(
    event: Event,
    push: PushMessage,
    content: JsonObject,
    body: String,
    caption: String?,
): Shown? {
    val local = event.localContent
    return when (content.text("msgtype") ?: event.effectiveType) {
        "m.text", "m.notice" -> {
            Shown(formatted(local?.sanitizedHtml, body, content))
        }

        "m.emote" -> {
            Shown(
                SpannableStringBuilder("* ${push.sender.name} ").append(formatted(local?.sanitizedHtml, body, content))
            )
        }

        "m.image", "m.sticker" -> {
            Shown(if (event.effectiveType == "m.sticker") STICKER else PHOTO, push.image ?: media(content), caption)
        }

        "m.video" -> {
            Shown(line("🎬", VIDEO, duration(content)), thumbnail(content), caption)
        }

        "m.audio" -> {
            Shown(audioLine(content, caption))
        }

        "m.file" -> {
            Shown(line("📎", content.text("filename") ?: body, size(content)))
        }

        "m.location" -> {
            Shown(line("📍", body.ifBlank { LOCATION }))
        }

        else -> {
            null
        }
    }
}

private const val VIDEO = "Video"
private const val PHOTO = "📷 Photo"
private const val STICKER = "Sticker"
private const val LOCATION = "Location"

private fun audioLine(
    content: JsonObject,
    caption: String?,
): String {
    val voice = content["org.matrix.msc3245.voice"] != null || content["m.voice"] != null
    return if (voice) {
        line("🎤", "Voice message", duration(content))
    } else {
        line(
            "🎵",
            caption ?: content.text("body") ?: "Audio",
            duration(content)
        )
    }
}

private fun line(
    emoji: String,
    text: String,
    vararg details: String?,
): String = (listOf("$emoji $text") + details.filterNotNull()).joinToString(" · ")

/** gomuks' sanitised HTML when there is one; otherwise the body, its common markdown honoured. */
private fun formatted(
    sanitizedHtml: String?,
    body: String,
    content: JsonObject,
): CharSequence {
    val html = sanitizedHtml?.takeIf { it.isNotBlank() && content.text("format") == HTML }
    return if (html != null) styledHtml(html) else styledMarkdown(withoutReplyFallback(body))
}

private const val HTML = "org.matrix.custom.html"

/** A plain reply's body begins with the quoted message (`> <@user> …` lines): not worth showing. */
private fun withoutReplyFallback(body: String): String =
    if (body.startsWith("> <")) {
        body
            .lineSequence()
            .dropWhile { it.startsWith(">") }
            .joinToString("\n")
            .trim()
    } else {
        body
    }

private fun duration(content: JsonObject): String? {
    val ms =
        (content["info"] as? JsonObject)?.get("duration")?.let { (it as? JsonPrimitive)?.longOrNull } ?: return null
    val s = ms / MS
    return "%d:%02d".format(s / SECONDS, s % SECONDS)
}

private fun size(content: JsonObject): String? {
    val bytes = (content["info"] as? JsonObject)?.get("size")?.let { (it as? JsonPrimitive)?.longOrNull } ?: return null
    return when {
        bytes >= MB -> "%.1f MB".format(bytes / MB.toDouble())
        bytes >= KB -> "${bytes / KB} KB"
        else -> "$bytes B"
    }
}

/** The media itself (a picture or sticker): its gomuks path, encrypted or not. */
private fun media(content: JsonObject): String? =
    path(content.text("url"), false) ?: path((content["file"] as? JsonObject)?.text("url"), true)

/** A video's thumbnail, if its sender attached one. */
private fun thumbnail(content: JsonObject): String? {
    val info = content["info"] as? JsonObject ?: return null
    return path(info.text("thumbnail_url"), false) ?: path((info["thumbnail_file"] as? JsonObject)?.text("url"), true)
}

private fun path(
    mxc: String?,
    encrypted: Boolean,
): String? {
    val rest = mxc?.removePrefix("mxc://")?.takeIf { it != mxc } ?: return null
    val (server, id) = rest.split('/').takeIf { it.size == 2 } ?: return null
    return "_gomuks/media/$server/$id?encrypted=$encrypted"
}

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private const val MS = 1000
private const val SECONDS = 60
private const val KB = 1024L
private const val MB = 1024L * 1024
