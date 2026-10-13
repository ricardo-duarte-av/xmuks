package pt.aguiarvieira.xmuks.feature.room

import pt.aguiarvieira.xmuks.core.data.timeline.Limit

/**
 * What the bridged network won't do with a message: those items stay, dimmed, and tapping one
 * says why ([onLimit]). [delete] only relabels deleting: the Matrix copy still goes.
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
    val onLimit: (Limit) -> Unit = {},
)
