package pt.aguiarvieira.xmuks.core.data.timeline

/**
 * Adds [item]; when it [continues] the previous message's group, that message stops being the
 * group's last and this one isn't its first.
 */
internal fun MutableList<TimelineItem>.appendGrouped(
    item: TimelineItem,
    continues: Boolean,
) {
    if (item !is TimelineItem.Message) {
        add(item)
        return
    }
    if (continues) {
        val last = last() as TimelineItem.Message
        set(lastIndex, last.copy(lastInGroup = false))
    }
    add(item.copy(firstInGroup = !continues))
}
