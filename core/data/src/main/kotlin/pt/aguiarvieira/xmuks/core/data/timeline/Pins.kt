package pt.aguiarvieira.xmuks.core.data.timeline

/** A room's pinned messages (oldest pin first), and whether we may pin or unpin. */
data class Pins(
    val eventIds: List<String> = emptyList(),
    val canPin: Boolean = false,
) {
    companion object {
        const val TYPE = "m.room.pinned_events"
    }
}
