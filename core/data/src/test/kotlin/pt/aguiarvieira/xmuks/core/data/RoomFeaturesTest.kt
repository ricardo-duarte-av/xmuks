package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.Action
import pt.aguiarvieira.xmuks.core.data.timeline.Limit
import pt.aguiarvieira.xmuks.core.data.timeline.MediaIssue
import pt.aguiarvieira.xmuks.core.data.timeline.RoomFeatures
import pt.aguiarvieira.xmuks.core.data.timeline.Support
import pt.aguiarvieira.xmuks.core.data.timeline.roomFeaturesOf
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

class RoomFeaturesTest {
    private fun json(s: String) = GomuksJson.parseToJsonElement(s) as JsonObject

    private fun state(
        content: String,
        key: String = "x/whatsapp",
        redacted: Boolean = false,
    ) = Event(
        rowId = 1,
        roomId = "!r",
        eventId = "\$f$key",
        sender = "@bot:x",
        type = RoomFeatures.TYPE,
        timestamp = 0,
        content = json(content),
        stateKey = key,
        redactedBy = if (redacted) "\$r" else null,
    )

    private val whatsApp =
        """
        {"block_user":true,"delete":2,"delete_chat":true,"delete_max_age":172800,
         "disappearing_timer":{"timers":[86400000,604800000,7776000000],"types":["after_send"]},
         "edit":2,"edit_max_age":900,
         "file":{
           "fi.mau.gif":{"caption":2,"max_caption_length":65536,"max_size":2097152000,"mime_types":{"image/gif":1,"video/mp4":2}},
           "m.audio":{"caption":-1,"max_size":2097152000,"mime_types":{"audio/aac":2,"audio/amr":2,"audio/mp4":2,"audio/mpeg":2,"audio/ogg":2}},
           "m.file":{"caption":2,"max_caption_length":65536,"max_size":2097152000,"mime_types":{"*/*":2}},
           "m.image":{"caption":2,"max_caption_length":65536,"max_size":2097152000,"mime_types":{"image/gif":1,"image/jpeg":2,"image/png":1,"image/webp":1}},
           "m.sticker":{"caption":-1,"max_size":2097152000,"mime_types":{"image/jpeg":1,"image/png":1,"image/webp":2,"video/lottie+json":1}},
           "m.video":{"caption":2,"max_caption_length":65536,"max_size":2097152000,"mime_types":{"video/3gpp":2,"video/mp4":2,"video/quicktime":1,"video/webm":1}},
           "org.matrix.msc3245.voice":{"caption":-1,"max_size":2097152000,"mime_types":{"audio/ogg":0,"audio/ogg; codecs=opus":2}}
         },
         "formatting":{"blockquote":2,"bold":2,"code_block":2,"headers":1,"inline_code":2,"inline_link":1,"italic":2,
           "ordered_list":2,"ordered_list.start":2,"strikethrough":2,"unordered_list":2,"user_link":2},
         "id":"fi.mau.whatsapp.capabilities.2026_10_01+ffmpeg+dm","location_message":2,"max_text_length":65536,
         "poll":2,"poll_max_options":12,"poll_option_max_length":100,"reaction":2,"reaction_count":1,
         "read_receipts":true,"reply":2,"state":{"com.beeper.disappearing_timer":{"level":2}},"typing_notifications":true}
        """.trimIndent()

    private val features = roomFeaturesOf(listOf(state(whatsApp)))!!

    @Test
    fun readsWhatsApp() {
        assertEquals(Support.Full, features.edit)
        assertEquals(900L, features.editMaxAgeSec)
        assertEquals(172800L, features.deleteMaxAgeSec)
        assertEquals(1, features.reactionCount)
        assertNull(features.allowedReactions)
        assertEquals(65536, features.maxTextLength)
        assertEquals(12, features.pollMaxOptions)
        assertEquals(Support.Partial, features.formatting["headers"])
        assertEquals(listOf(86400000L, 604800000L, 7776000000L), features.disappearingTimers)
        assertTrue(features.typingNotifications)
        assertTrue(features.voiceMessages)
        // Left out means unsupported: WhatsApp has no threads.
        assertEquals(Support.Unsupported, features.thread)
        assertEquals(Limit.Unsupported(Action.Thread), features.threadBlock())
        assertNull(features.replyBlock())
    }

    @Test
    fun noFeaturesWithoutTheEvent() {
        assertNull(roomFeaturesOf(emptyList()))
        assertNull(roomFeaturesOf(listOf(state(whatsApp, redacted = true))))
    }

    @Test
    fun editsAndDeletesHaveWindows() {
        val minute = 60_000L
        assertNull(features.editBlock(sentAt = 0, now = 14 * minute))
        assertEquals(Limit.TooOld(Action.Edit, 900), features.editBlock(sentAt = 0, now = 16 * minute))
        assertNull(features.deleteBlock(sentAt = 0, now = 47 * 60 * minute))
        assertEquals(Limit.TooOld(Action.Delete, 172800), features.deleteBlock(sentAt = 0, now = 49 * 60 * minute))
    }

    @Test
    fun checksMedia() {
        assertEquals(emptyList<MediaIssue>(), features.checkMedia(RoomFeatures.IMAGE, "image/jpeg", 1000))
        assertEquals(listOf(MediaIssue.Converted), features.checkMedia(RoomFeatures.IMAGE, "image/png", 1000))
        assertEquals(
            listOf(MediaIssue.FormatUnsupported("image/heic")),
            features.checkMedia(RoomFeatures.IMAGE, "image/heic", 1000),
        )
        assertEquals(
            listOf(MediaIssue.TooBig(2097152000)),
            features.checkMedia(RoomFeatures.VIDEO, "video/mp4", 3_000_000_000),
        )
        assertEquals(
            listOf(MediaIssue.CaptionDropped),
            features.checkMedia(RoomFeatures.AUDIO, "audio/mpeg", 1000, captionLength = 5),
        )
        // Any file goes as a file.
        assertEquals(emptyList<MediaIssue>(), features.checkMedia(RoomFeatures.FILE, "application/x-thing", 1000))
        // Voice must be Opus, said so.
        assertEquals(emptyList<MediaIssue>(), features.checkMedia(RoomFeatures.VOICE, RoomFeatures.VOICE_MIME, 1000))
        assertTrue(features.checkMedia(RoomFeatures.VOICE, "audio/ogg", 1000).single().blocks)
        assertEquals(
            listOf(MediaIssue.KindUnsupported("m.location")),
            features.checkMedia("m.location", null, 0),
        )
    }

    @Test
    fun reactions() {
        assertNull(features.reactionBlock("👍", custom = false))
        assertEquals(Limit.ReactionNotAllowed, features.reactionBlock("mxc://x/y", custom = true))
        val limited = roomFeaturesOf(listOf(state("""{"reaction":2,"allowed_reactions":["👍","❤️"]}""")))!!
        assertNull(limited.reactionBlock("👍", custom = false))
        assertEquals(Limit.ReactionNotAllowed, limited.reactionBlock("🤔", custom = false))
        val none = roomFeaturesOf(listOf(state("{}")))!!
        assertEquals(Limit.Unsupported(Action.React), none.reactionBlock("👍", custom = false))
    }

    @Test
    fun twoBridgesMergeToTheStrictest() {
        val other =
            """
            {"edit":1,"edit_max_age":300,"delete":2,"reaction":2,"reaction_count":3,"max_text_length":4096,
             "thread":2,"read_receipts":false,
             "file":{"m.image":{"caption":2,"max_size":1000,"mime_types":{"image/*":2}}}}
            """.trimIndent()
        val merged = roomFeaturesOf(listOf(state(whatsApp), state(other, key = "x/signal")))!!
        assertEquals(Support.Partial, merged.edit)
        assertEquals(300L, merged.editMaxAgeSec)
        assertEquals(172800L, merged.deleteMaxAgeSec)
        assertEquals(1, merged.reactionCount)
        assertEquals(4096, merged.maxTextLength)
        assertEquals(Support.Unsupported, merged.thread)
        assertFalse(merged.readReceipts)
        assertEquals(setOf(RoomFeatures.IMAGE), merged.files.keys)
        assertEquals(
            listOf(MediaIssue.Converted, MediaIssue.TooBig(1000)),
            merged.checkMedia(RoomFeatures.IMAGE, "image/png", 2000)
        )
        assertEquals(
            listOf(MediaIssue.FormatUnsupported("image/heic")),
            merged.checkMedia(RoomFeatures.IMAGE, "image/heic", 10)
        )
        assertEquals(Limit.TextTooLong(4096), merged.textBlock(5000))
    }
}
