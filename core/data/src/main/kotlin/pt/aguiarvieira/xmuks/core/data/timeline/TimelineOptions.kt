package pt.aguiarvieira.xmuks.core.data.timeline

import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs

/** What the timeline leaves out, from gomuks' preferences. */
data class TimelineOptions(
    val showRedacted: Boolean = true,
    val showMembership: Boolean = true,
    val showProfileChanges: Boolean = true,
    val showDateSeparators: Boolean = true,
    val showReadReceipts: Boolean = true,
    /** Events with nothing to render of their own, as their type (gomuks' `show_hidden_events`). */
    val showHidden: Boolean = true,
    /** Link previews bundled in messages (MSC4095, gomuks' `render_url_previews`). */
    val showUrlPreviews: Boolean = true,
) {
    /** Whether [item] is shown at all. */
    fun shows(item: TimelineItem): Boolean =
        when (item) {
            is TimelineItem.Message -> showRedacted || item.content != MessageContent.Redacted
            is TimelineItem.StateChange -> shows(item.change)
            is TimelineItem.Hidden -> showHidden
            else -> true
        }

    private fun shows(change: Change): Boolean =
        when (change) {
            Change.Joined, Change.Left, is Change.Invited, is Change.Kicked, is Change.Banned -> showMembership
            is Change.ProfileChanged -> showProfileChanges
            else -> true
        }

    companion object {
        fun of(layers: PrefLayers) =
            TimelineOptions(
                showRedacted = layers.get(Prefs.showRedactedEvents),
                showMembership = layers.get(Prefs.showMembershipEvents),
                showProfileChanges = layers.get(Prefs.showProfileChanges),
                showDateSeparators = layers.get(Prefs.showDateSeparators),
                showReadReceipts = layers.get(Prefs.displayReadReceipts),
                showHidden = layers.get(Prefs.showHiddenEvents),
                showUrlPreviews = layers.get(Prefs.renderUrlPreviews),
            )
    }
}
