package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.timeline.Change
import pt.aguiarvieira.xmuks.core.data.timeline.FileKeys
import pt.aguiarvieira.xmuks.core.data.timeline.MemberProfile
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent
import pt.aguiarvieira.xmuks.core.data.timeline.ReplyKind
import pt.aguiarvieira.xmuks.core.data.timeline.SendState
import pt.aguiarvieira.xmuks.core.data.timeline.SenderNetwork
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItemBuilder
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineOptions
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineSnapshot
import pt.aguiarvieira.xmuks.core.data.timeline.previewHtmlOf
import pt.aguiarvieira.xmuks.core.protocol.Event
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.LocalContent
import pt.aguiarvieira.xmuks.core.protocol.Receipt
import java.time.ZoneOffset

class TimelineItemBuilderTest {
    private val builder = TimelineItemBuilder(me = "@me:x", zone = ZoneOffset.UTC)
    private var row = 0L
    private val t0 = 1_790_000_000_000L // 2026-09-21 in UTC

    private fun json(s: String): JsonObject = GomuksJson.parseToJsonElement(s).jsonObject

    private fun ev(
        sender: String = "@bob:x",
        type: String = "m.room.message",
        content: String = """{"msgtype":"m.text","body":"hi"}""",
        ts: Long = t0,
        stateKey: String? = null,
        block: Event.() -> Event = { this },
    ): Event {
        row++
        return Event(
            rowId = row,
            timelineRowId = row,
            roomId = "!r",
            eventId = "\$e$row",
            sender = sender,
            type = type,
            stateKey = stateKey,
            timestamp = ts,
            content = json(content),
        ).block()
    }

    private fun build(
        vararg events: Event,
        extra: List<Event> = emptyList(),
        profiles: Map<String, MemberProfile> = emptyMap(),
        networks: Map<String, SenderNetwork> = emptyMap(),
    ) = builder.build(
        TimelineSnapshot("!r", events = events.toList(), eventsByRowId = (events.toList() + extra).associateBy { it.rowId }, loaded = true),
        profiles,
        networks,
    )

    private fun List<TimelineItem>.messages() = filterIsInstance<TimelineItem.Message>()

    @Test
    fun `reactions, edits and redaction events are not shown on their own`() {
        val msg = ev()
        val items =
            build(
                msg,
                ev(type = "m.reaction", content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${msg.eventId}","key":"👍"}}"""),
                ev(content = """{"msgtype":"m.text","body":"* x"}""") { copy(relationType = "m.replace") },
                ev(type = "m.room.redaction", content = "{}"),
            )
        assertEquals(1, items.messages().size)
    }

    @Test
    fun `an edited message shows its latest content and is marked edited`() {
        val edited = """{"msgtype":"m.text","body":"* fixed","m.new_content":{"msgtype":"m.text","body":"fixed"}}"""
        val edit =
            ev(content = edited) {
                copy(relationType = "m.replace", localContent = LocalContent(sanitizedHtml = "<b>fixed</b>"))
            }
        val original = ev(content = """{"msgtype":"m.text","body":"fxied"}""") { copy(lastEditRowId = edit.rowId) }
        val msg = build(original, extra = listOf(edit)).messages().single()
        val text = msg.content as MessageContent.Text
        assertEquals("fixed", text.body)
        assertEquals("<b>fixed</b>", text.html)
        assertTrue(msg.edited)
    }

    @Test
    fun `names come from per-message profile, then per-room member, then localpart`() {
        val profiles = mapOf("@bob:x" to MemberProfile("Bob in this room", "mxc://x/bob"))
        val items =
            build(
                ev(content = """{"msgtype":"m.text","body":"a","com.beeper.per_message_profile":{"id":"cat","displayname":"Cat","avatar_url":"mxc://x/cat"}}"""),
                ev(ts = t0 + 1_000),
                ev(sender = "@stranger:x", ts = t0 + 2_000),
                profiles = profiles,
            ).messages()
        assertEquals(listOf("Cat via Bob in this room", "Bob in this room", "stranger"), items.map { it.senderName })
        assertEquals(listOf("cat", null, null), items.map { it.label.profileId })
        assertEquals(listOf("mxc://x/cat", "mxc://x/bob", null), items.map { it.senderAvatarMxc })
    }

    @Test
    fun `bridged senders carry their network, matched by protocol ID`() {
        val whatsapp = SenderNetwork("WhatsApp", "mxc://maunium.net/wa")
        val signal = SenderNetwork("Signal", "mxc://maunium.net/signal")
        val profiles =
            mapOf(
                "@wa_1:x" to MemberProfile("Thalita", null, network = "whatsapp"),
                "@bob:x" to MemberProfile("Bob", null),
            )
        val items =
            build(
                ev(sender = "@wa_1:x"),
                ev(sender = "@bob:x", ts = t0 + 1_000),
                ev(
                    sender = "@wa_1:x",
                    ts = t0 + 2_000,
                    content = """{"msgtype":"m.text","body":"a","com.beeper.per_message_profile":{"id":"cat","displayname":"Cat"}}""",
                ),
                profiles = profiles,
                networks = mapOf("whatsapp" to whatsapp, "signal" to signal),
            ).messages()
        assertEquals(listOf(whatsapp, null, null), items.map { it.senderNetwork })
    }

    @Test
    fun `a bridged sender's network falls back to the room's only bridge, and is read from member events`() {
        val wa = SenderNetwork("WhatsApp", null)
        val join = """{"membership":"join","displayname":"T","com.beeper.bridge.network":"whatsapp-lid"}"""
        val items =
            build(
                ev(sender = "@wa_1:x", type = "m.room.member", stateKey = "@wa_1:x", content = join),
                ev(sender = "@wa_1:x", ts = t0 + 1_000),
                networks = mapOf("whatsapp" to wa),
            ).messages()
        assertEquals(wa, items.single().senderNetwork)
    }

    @Test
    fun `messages group by sender and profile within five minutes, days are separated`() {
        val items =
            build(
                ev(ts = t0),
                ev(ts = t0 + 60_000),
                ev(ts = t0 + 60_000 * 10),
                ev(ts = t0 + 60_000 * 11, content = """{"msgtype":"m.text","body":"b","m.per_message_profile":{"id":"other"}}"""),
                ev(ts = t0 + 86_400_000),
            )
        val msgs = items.messages()
        assertEquals(listOf(true, false, true, true, true), msgs.map { it.firstInGroup })
        assertEquals(listOf(false, true, true, true, true), msgs.map { it.lastInGroup })
        assertEquals(2, items.count { it is TimelineItem.DaySeparator })
    }

    @Test
    fun `replies resolve the original, or say it isn't loaded`() {
        val original = ev(sender = "@ann:x", content = """{"msgtype":"m.text","body":"question?"}""")
        val reply = ev(content = """{"msgtype":"m.text","body":"answer","m.relates_to":{"m.in_reply_to":{"event_id":"${original.eventId}"}}}""")
        val dangling = ev(content = """{"msgtype":"m.text","body":"?","m.relates_to":{"m.in_reply_to":{"event_id":"${'$'}gone"}}}""")
        val msgs = build(reply, dangling, extra = listOf(original)).messages()
        assertEquals("ann", msgs[0].reply!!.senderName)
        assertEquals("question?", msgs[0].reply!!.text)
        assertNull(msgs[1].reply!!.senderName)
    }

    @Test
    fun `a reply to something that isn't a message says what it was`() {
        val reaction = ev(type = "m.reaction", content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${'$'}x","key":"👍"}}""")
        val join = ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join"}""")
        val avatar =
            ev(
                sender = "@ann:x",
                type = "m.room.member",
                stateKey = "@ann:x",
                content = """{"membership":"join","displayname":"Ann","avatar_url":"mxc://x/new"}""",
            ) { copy(unsigned = json("""{"prev_content":{"membership":"join","displayname":"Ann","avatar_url":"mxc://x/old"}}""")) }
        val kick = ev(sender = "@mod:x", type = "m.room.member", stateKey = "@spam:x", content = """{"membership":"leave","displayname":"Spammer"}""")
        val pins = ev(type = "m.room.pinned_events", stateKey = "", content = """{"pinned":["${'$'}x"]}""")
        val odd = ev(type = "org.example.thing", content = "{}")
        val originals = listOf(reaction, join, avatar, kick, pins, odd)
        val replies =
            originals.map {
                ev(content = """{"msgtype":"m.text","body":"re","m.relates_to":{"m.in_reply_to":{"event_id":"${it.eventId}"}}}""")
            }
        val kinds = build(*replies.toTypedArray(), extra = originals).messages().map { it.reply!!.kind }
        assertEquals(ReplyKind.Reaction("👍"), kinds[0])
        assertEquals(ReplyKind.Changed(Change.Joined), kinds[1])
        val profile = (kinds[2] as ReplyKind.Changed).change as Change.ProfileChanged
        assertTrue(profile.avatarChanged)
        assertFalse(profile.nameChanged)
        assertEquals(ReplyKind.Changed(Change.Kicked("Spammer", null)), kinds[3])
        assertEquals(ReplyKind.PinsChanged, kinds[4])
        assertEquals(ReplyKind.Other("org.example.thing"), kinds[5])
    }

    @Test
    fun `a reply to a message with text has no kind`() {
        val original = ev(sender = "@ann:x", content = """{"msgtype":"m.text","body":"question?"}""")
        val reply = ev(content = """{"msgtype":"m.text","body":"answer","m.relates_to":{"m.in_reply_to":{"event_id":"${original.eventId}"}}}""")
        assertNull(build(reply, extra = listOf(original)).messages()[0].reply!!.kind)
    }

    @Test
    fun `reactions are sorted, ours marked, custom emoji recognised`() {
        val msg = ev { copy(reactions = mapOf("👍" to 1, "mxc://x/parrot" to 3)) }
        val mine = ev(sender = "@me:x", type = "m.reaction", content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${msg.eventId}","key":"👍"}}""")
        val reactions = build(msg, mine).messages().single().reactions
        assertEquals(listOf("mxc://x/parrot", "👍"), reactions.map { it.key })
        assertTrue(reactions[0].isImage)
        assertTrue(reactions[1].mine)
        assertFalse(reactions[0].mine)
    }

    @Test
    fun `media, redactions and undecryptable events`() {
        val image =
            ev(
                content =
                    """{"msgtype":"m.image","body":"look at this","filename":"cat.jpg",
                    "file":{"url":"mxc://x/enc"},"info":{"w":800,"h":600,"mimetype":"image/jpeg","xyz.amorgan.blurhash":"LEHV6nWB2y"}}""",
            )
        val redacted = ev { copy(redactedBy = "\$r") }
        val broken = ev(type = "m.room.encrypted", content = """{"algorithm":"m.megolm.v1.aes-sha2"}""") { copy(decryptionError = "no session") }
        val msgs = build(image, redacted, broken).messages()
        val img = msgs[0].content as MessageContent.Image
        assertEquals("mxc://x/enc", img.media.mxc)
        assertTrue(img.media.encrypted)
        assertEquals("look at this", img.caption)
        assertEquals("LEHV6nWB2y", img.media.blurhash)
        assertEquals(MessageContent.Redacted, msgs[1].content)
        assertEquals(MessageContent.Undecryptable("no session"), msgs[2].content)
    }

    @Test
    fun `galleries, stable and unstable, keep their items in order and their caption`() {
        val items =
            """[{"itemtype":"m.image","body":"a.jpg","url":"mxc://x/a","info":{"w":1500,"h":2000,"thumbnail_url":"mxc://x/at"}},
            {"itemtype":"m.video","body":"b.mp4","file":{"url":"mxc://x/b","key":{"k":"K"},"iv":"IV","hashes":{"sha256":"H"}}},
            {"itemtype":"m.unknown","body":"?"},
            {"itemtype":"m.file","body":"c.pdf","url":"mxc://x/c"}]"""
        val unstable = ev(content = """{"msgtype":"dm.filament.gallery","body":"","itemtypes":$items}""")
        val stable = ev(content = """{"msgtype":"m.gallery","body":"holiday","itemtypes":$items}""")
        val empty = ev(content = """{"msgtype":"m.gallery","body":"","itemtypes":[]}""")
        val reply = ev(content = """{"msgtype":"m.text","body":"nice","m.relates_to":{"m.in_reply_to":{"event_id":"${unstable.eventId}"}}}""")
        val msgs = build(unstable, stable, empty, reply).messages()
        val gallery = msgs[0].content as MessageContent.Gallery
        assertEquals(3, gallery.items.size)
        assertEquals("mxc://x/at", (gallery.items[0] as MessageContent.Image).media.thumbnailMxc)
        val video = (gallery.items[1] as MessageContent.Video).media
        assertTrue(video.encrypted)
        assertEquals(FileKeys("K", "IV", "H"), video.keys)
        assertEquals("c.pdf", (gallery.items[2] as MessageContent.File).name)
        assertNull(gallery.caption)
        assertEquals("holiday", (msgs[1].content as MessageContent.Gallery).caption)
        assertTrue(msgs[2].content is MessageContent.Unsupported)
        assertEquals("🖼️ 3 items", msgs[3].reply?.text)
    }

    @Test
    fun `membership changes`() {
        val items =
            build(
                ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join","displayname":"Ann"}"""),
                ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join","displayname":"Annie"}""") {
                    copy(unsigned = json("""{"prev_content":{"membership":"join","displayname":"Ann"}}"""))
                },
                ev(sender = "@mod:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"leave","reason":"spam"}"""),
            ).filterIsInstance<TimelineItem.StateChange>()
        assertEquals(Change.Joined, items[0].change)
        assertEquals(Change.ProfileChanged("Ann", "Annie", null, null, nameChanged = true, avatarChanged = false), items[1].change)
        assertEquals(Change.Kicked("Annie", "spam"), items[2].change)
    }

    @Test
    fun `sanitised html wins, then formatted_body, then plain`() {
        val msgs =
            build(
                ev(content = """{"msgtype":"m.text","body":"a.b","format":"org.matrix.custom.html","formatted_body":"<i>raw</i>"}""") {
                    copy(localContent = LocalContent(sanitizedHtml = "<i>clean</i>", wasPlaintext = true))
                },
                ev(content = """{"msgtype":"m.text","body":"x","format":"org.matrix.custom.html","formatted_body":"<i>raw</i>"}"""),
                ev(content = """{"msgtype":"m.text","body":"plain"}"""),
            ).messages()
        assertEquals(listOf("<i>clean</i>", "<i>raw</i>", null), msgs.map { (it.content as MessageContent.Text).html })
    }

    @Test
    fun `a formatted caption keeps its html, a plain one doesn't`() {
        val img = """"msgtype":"m.image","url":"mxc://s/i","filename":"cat.jpg""""
        val msgs =
            build(
                ev(content = """{$img,"body":"**cat**","format":"org.matrix.custom.html","formatted_body":"<b>cat</b>"}""") {
                    copy(localContent = LocalContent(sanitizedHtml = "<b>cat</b>"))
                },
                ev(content = """{$img,"body":"cat.org"}""") {
                    copy(localContent = LocalContent(sanitizedHtml = "<a href=\"https://cat.org\">cat.org</a>", wasPlaintext = true))
                },
            ).messages()
        assertEquals(listOf("<b>cat</b>", null), msgs.map { (it.content as MessageContent.Image).html })
    }

    @Test
    fun `the room list preview keeps formatting only where the sender gave it`() {
        val html = """"format":"org.matrix.custom.html","formatted_body":"<b>x</b>""""
        val formatted = LocalContent(sanitizedHtml = "<b>x</b>", previewText = "**x**")
        assertEquals("<b>x</b>", previewHtmlOf(json("""{"msgtype":"m.text","body":"**x**",$html}"""), formatted))
        assertEquals(
            "<b>x</b>",
            previewHtmlOf(json("""{"msgtype":"m.image","body":"**x**","filename":"a.jpg",$html}"""), formatted)
        )
        // No caption: the preview is gomuks' "Sent an image".
        assertNull(previewHtmlOf(json("""{"msgtype":"m.image","body":"a.jpg",$html}"""), formatted))
        // Linkified plain text isn't formatting.
        assertNull(
            previewHtmlOf(
                json("""{"msgtype":"m.text","body":"a.b"}"""),
                LocalContent(sanitizedHtml = "a.b", previewText = "a.b", wasPlaintext = true)
            )
        )
    }

    @Test
    fun `gomuks' not-sent placeholder is not an error`() {
        val msgs = build(ev { copy(sendError = "not sent") }, ev { copy(sendError = "M_FORBIDDEN") }).messages()
        assertEquals(listOf(null, "M_FORBIDDEN"), msgs.map { it.sendError })
    }

    @Test
    fun `an emote stands alone and ends the group around it`() {
        val msgs =
            build(
                ev(ts = t0),
                ev(ts = t0 + 1_000, content = """{"msgtype":"m.emote","body":"waves"}"""),
                ev(ts = t0 + 2_000),
            ).messages()
        assertEquals(listOf(true, true, true), msgs.map { it.firstInGroup })
        assertEquals(listOf(true, true, true), msgs.map { it.lastInGroup })
    }

    @Test
    fun `state changes carry what replies and reactions need`() {
        val join =
            ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join","displayname":"Ann","avatar_url":"mxc://x/ann"}""") {
                copy(reactions = mapOf("👋" to 2))
            }
        val change = build(join).single { it is TimelineItem.StateChange }
        change as TimelineItem.StateChange
        assertEquals(join.eventId, change.eventId)
        assertEquals("@ann:x", change.actor)
        assertEquals("mxc://x/ann", change.actorAvatarMxc)
        assertEquals(listOf("👋"), change.reactions.map { it.key })
    }

    @Test
    fun `reactions whose count dropped to zero are not shown`() {
        val msg = ev { copy(reactions = mapOf("🟢 build" to 1, "🔵 build" to 0)) }
        assertEquals(
            listOf("🟢 build"),
            build(msg)
                .messages()
                .single()
                .reactions
                .map { it.key }
        )
    }

    @Test
    fun `a receipt on a hidden event counts for the shown event before it, once per user`() {
        val first = ev()
        val second = ev()
        val reaction = ev(type = "m.reaction", content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${second.eventId}","key":"👍"}}""")

        fun receipt(
            user: String,
            on: Event,
            ts: Long,
        ) = Receipt(userId = user, receiptType = "m.read", eventId = on.eventId, timestamp = ts)
        val snapshot =
            TimelineSnapshot(
                "!r",
                events = listOf(first, second, reaction),
                eventsByRowId = listOf(first, second, reaction).associateBy { it.rowId },
                receiptsByEventId =
                    mapOf(
                        first.eventId to listOf(receipt("@ann:x", first, 1)),
                        reaction.eventId to listOf(receipt("@cat:x", reaction, 2), receipt("@me:x", reaction, 2)),
                    ),
                loaded = true,
            )
        val msgs = builder.build(snapshot, emptyMap()).messages()
        assertEquals(listOf("@ann:x"), msgs[0].readBy.map { it.userId })
        assertEquals(listOf("@cat:x"), msgs[1].readBy.map { it.userId }) // not us
    }

    @Test
    fun `linkified plain text keeps its line breaks, real html doesn't`() {
        val plain =
            ev(content = """{"msgtype":"m.text","body":"a.b\nc"}""") {
                copy(localContent = LocalContent(sanitizedHtml = "a.b\nc", wasPlaintext = true))
            }
        val html = ev(content = """{"msgtype":"m.text","body":"x","format":"org.matrix.custom.html","formatted_body":"<b>x</b>"}""")
        val msgs = build(plain, html).messages().map { it.content as MessageContent.Text }
        assertEquals(listOf(true, false), msgs.map { it.plainText })
    }

    @Test
    fun `avatar and name changes are one profile change`() {
        val change =
            build(
                ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join","displayname":"Annie","avatar_url":"mxc://x/new"}""") {
                    copy(unsigned = json("""{"prev_content":{"membership":"join","displayname":"Ann","avatar_url":"mxc://x/old"}}"""))
                },
            ).filterIsInstance<TimelineItem.StateChange>().single().change
        assertEquals(Change.ProfileChanged("Ann", "Annie", "mxc://x/old", "mxc://x/new", nameChanged = true, avatarChanged = true), change)
    }

    @Test
    fun `a local echo is sending until it has a real event ID, a real send error fails it`() {
        val echo = ev(sender = "@me:x") { copy(eventId = "~hicli-txn", sendError = "not sent") }
        val sent = ev(sender = "@me:x") { copy(sendError = "not sent") }
        val failed = ev(sender = "@me:x") { copy(eventId = "~hicli-txn2", sendError = "M_FORBIDDEN") }
        assertEquals(
            listOf(SendState.Sending, SendState.Sent, SendState.Failed),
            build(echo, sent, failed).messages().map { it.sendState },
        )
    }

    @Test
    fun `only our own text messages are editable, from gomuks' edit source`() {
        val mine =
            ev(sender = "@me:x", content = """{"msgtype":"m.emote","body":"waves"}""") {
                copy(localContent = LocalContent(editSource = "/me waves"))
            }
        val plain = ev(sender = "@me:x", content = """{"msgtype":"m.text","body":"hi"}""")
        val theirs = ev(content = """{"msgtype":"m.text","body":"yo"}""")
        val audio = ev(sender = "@me:x", content = """{"msgtype":"m.audio","body":"x.ogg","url":"mxc://x/y"}""")
        assertEquals(listOf("/me waves", "hi", null, null), build(mine, plain, theirs, audio).messages().map { it.editSource })
    }

    @Test
    fun `our pictures, videos, files and galleries edit their caption - empty when they have none`() {
        val bare = ev(sender = "@me:x", content = """{"msgtype":"m.image","body":"x.png","url":"mxc://x/y"}""")
        val captioned =
            ev(sender = "@me:x", content = """{"msgtype":"m.video","body":"look","filename":"v.mp4","url":"mxc://x/v"}""")
        val file =
            ev(sender = "@me:x", content = """{"msgtype":"m.file","body":"notes","filename":"a.pdf","url":"mxc://x/f"}""")
        val theirs = ev(content = """{"msgtype":"m.image","body":"hi","filename":"y.png","url":"mxc://x/z"}""")
        assertEquals(listOf("", "look", "notes", null), build(bare, captioned, file, theirs).messages().map { it.editSource })
    }

    @Test
    fun `a file keeps its caption apart from its name`() {
        val captioned = ev(content = """{"msgtype":"m.file","body":"the report","filename":"q3.pdf","url":"mxc://x/a"}""")
        val bare = ev(content = """{"msgtype":"m.file","body":"q4.pdf","url":"mxc://x/b"}""")
        val files = build(captioned, bare).messages().map { it.content as MessageContent.File }
        assertEquals("q3.pdf", files[0].name)
        assertEquals("the report", files[0].caption)
        assertEquals("q4.pdf", files[1].name)
        assertNull(files[1].caption)
    }

    @Test
    fun `preferences leave out membership, profile changes, deletions and dates, and groups close over them`() {
        val quiet =
            TimelineItemBuilder(
                me = "@me:x",
                options = TimelineOptions(showRedacted = false, showMembership = false, showProfileChanges = false, showDateSeparators = false),
                zone = ZoneOffset.UTC,
            )
        val events =
            listOf(
                ev(ts = t0),
                ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join"}""", ts = t0 + 1_000),
                ev(content = "{}", ts = t0 + 2_000) { copy(redactedBy = "\$x") },
                ev(sender = "@ann:x", type = "m.room.member", stateKey = "@ann:x", content = """{"membership":"join","displayname":"Annie"}""", ts = t0 + 3_000) {
                    copy(unsigned = json("""{"prev_content":{"membership":"join","displayname":"Ann"}}"""))
                },
                ev(ts = t0 + 4_000),
            )
        val items = quiet.build(TimelineSnapshot("!r", events = events, eventsByRowId = events.associateBy { it.rowId }, loaded = true), emptyMap())
        assertEquals(2, items.size)
        val (first, second) = items.messages()
        // The two messages still read as one group: what was between them is gone.
        assertTrue(first.firstInGroup && !first.lastInGroup)
        assertTrue(!second.firstInGroup && second.lastInGroup)
    }

    @Test
    fun `hidden events show as their type, unless that's turned off`() {
        val msg = ev()
        val events =
            arrayOf(
                msg,
                ev(type = "m.reaction", content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${msg.eventId}","key":"👍"}}"""),
                ev(type = "com.beeper.message_send_status", content = "{}"),
            )
        val hidden = build(*events).filterIsInstance<TimelineItem.Hidden>()
        assertEquals(listOf("m.reaction", "com.beeper.message_send_status"), hidden.map { it.type })
        val quiet = TimelineItemBuilder(me = "@me:x", options = TimelineOptions(showHidden = false), zone = ZoneOffset.UTC)
        val items = quiet.build(TimelineSnapshot("!r", events = events.toList(), eventsByRowId = events.associateBy { it.rowId }, loaded = true), emptyMap())
        assertTrue(items.none { it is TimelineItem.Hidden })
    }

    @Test
    fun `hidden events carry their reactions and whether they're ours`() {
        val status = ev(sender = "@me:x", type = "com.beeper.message_send_status", content = "{}") { copy(reactions = mapOf("👍" to 2)) }
        val mine = ev(sender = "@me:x", type = "m.reaction", content = """{"m.relates_to":{"rel_type":"m.annotation","event_id":"${status.eventId}","key":"👍"}}""")
        val hidden = build(status, mine).filterIsInstance<TimelineItem.Hidden>().first()
        assertTrue(hidden.fromMe)
        assertEquals(listOf("👍"), hidden.reactions.map { it.key })
        assertTrue(hidden.reactions.single().mine)
    }

    @Test
    fun `a message mentions us by name or as the room, never our own`() {
        val byName = ev(content = """{"msgtype":"m.text","body":"yeah","m.mentions":{"user_ids":["@me:x"]}}""")
        val room = ev(content = """{"msgtype":"m.text","body":"morning @room","m.mentions":{"room":true}}""")
        val other = ev(content = """{"msgtype":"m.text","body":"hi ann","m.mentions":{"user_ids":["@ann:x"]}}""")
        val plain = ev(content = """{"msgtype":"m.text","body":"hi me"}""")
        val ours = ev(sender = "@me:x", content = """{"msgtype":"m.text","body":"@room!","m.mentions":{"room":true}}""")
        val shown = build(byName, room, other, plain, ours).messages().map { it.mentionsMe }
        assertEquals(listOf(true, true, false, false, false), shown)
    }

    @Test
    fun `an edit's new content decides whether a message mentions us`() {
        val edited = """{"msgtype":"m.text","body":"* hi","m.mentions":{},"m.new_content":{"msgtype":"m.text","body":"hi","m.mentions":{"user_ids":["@me:x"]}}}"""
        val edit = ev(content = edited) { copy(relationType = "m.replace") }
        val original = ev(content = """{"msgtype":"m.text","body":"hi"}""") { copy(lastEditRowId = edit.rowId) }
        assertTrue(build(original, extra = listOf(edit)).messages().single().mentionsMe)
    }
}
