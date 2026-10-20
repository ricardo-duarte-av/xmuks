package pt.aguiarvieira.xmuks.core.notify

import pt.aguiarvieira.xmuks.core.protocol.Event

/** A past notification, rebuilt from its event (the watch's list of them): what its notification showed. */
data class Notified(
    /** The message as a push would have carried it: room, sender, per-message profile. */
    val message: PushMessage,
    val text: CharSequence,
    /** A gomuks media path (`_gomuks/media/…`), relative to the server; null for none. */
    val picture: String? = null,
    val caption: CharSequence? = null,
)

/** [event] shown as its notification shows it, with [message] standing in for the push. */
fun notifiedOf(
    event: Event,
    message: PushMessage,
): Notified {
    val shown = shownOf(event, message)
    return Notified(message, shown.text, shown.picture, shown.caption)
}
