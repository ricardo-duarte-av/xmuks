package pt.aguiarvieira.xmuks.core.protocol.rtc

/** Event types and constants of MatrixRTC calls as Element Call speaks them today. */
object RtcTypes {
    /** Legacy (state event) membership. */
    const val LEGACY_MEMBER = "org.matrix.msc3401.call.member"

    /** Sticky (MSC4354) membership. */
    const val STICKY_MEMBER = "org.matrix.msc4143.rtc.member"

    /** Slot state: `{status: open|closed, ...}`, state key = slot id. Nobody creates these yet. */
    const val SLOT = "org.matrix.msc4143.rtc.slot"

    /** "Ring" / "a call started" (MSC4075). */
    const val NOTIFICATION = "org.matrix.msc4075.rtc.notification"

    /** Declining a ring (MSC4310). */
    const val DECLINE = "org.matrix.msc4310.rtc.decline"

    /** Per-participant media keys, Olm-encrypted to-device. */
    const val ENCRYPTION_KEYS = "io.element.call.encryption_keys"

    /** In-call emoji reactions (references our membership event). */
    const val CALL_REACTION = "io.element.call.reaction"

    const val APPLICATION_CALL = "m.call"
    const val TRANSPORT_LIVEKIT = "livekit"
    const val INTENT_KEY = "m.call.intent"
    const val INTENT_AUDIO = "audio"
    const val INTENT_VIDEO = "video"

    /** Every event type that belongs to call signalling rather than conversation. */
    val SIGNALLING = setOf(LEGACY_MEMBER, STICKY_MEMBER, SLOT, NOTIFICATION, DECLINE, CALL_REACTION)
}
