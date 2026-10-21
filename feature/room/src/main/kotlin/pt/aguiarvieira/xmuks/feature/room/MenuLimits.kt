package pt.aguiarvieira.xmuks.feature.room

import pt.aguiarvieira.xmuks.core.data.timeline.Limit

/**
 * What the bridged network won't do with a message: those items stay, dimmed, and tapping one
 * says why ([onLimit]). [delete] only relabels deleting: the Matrix copy still goes. [write]
 * dims everything that sends, network or not (a preview).
 */
internal class MenuLimits(
    val edit: Limit? = null,
    val delete: Limit? = null,
    val reply: Limit? = null,
    val thread: Limit? = null,
    /** Why a quick reaction won't do. */
    val reaction: (String) -> Limit? = { null },
    /** No reactions at all. */
    val moreReactions: Limit? = null,
    /** Pinning, ending a poll, deleting: whatever the network. */
    val write: Limit? = null,
    val onLimit: (Limit) -> Unit = {},
) {
    companion object {
        /** A preview's: every item that would send is dimmed, and says so. */
        fun preview(onLimit: (Limit) -> Unit): MenuLimits =
            Limit.Preview.let {
                MenuLimits(
                    edit = it,
                    reply = it,
                    thread = it,
                    reaction = { _ -> it },
                    moreReactions = it,
                    write = it,
                    onLimit = onLimit,
                )
            }
    }
}
