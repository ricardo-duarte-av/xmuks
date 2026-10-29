package pt.aguiarvieira.xmuks.core.data.rooms

/**
 * Top-level space order as MSC3230 has it (Element and gomuks read it): each space may carry an
 * `order` string in its `org.matrix.msc3230.space_order` room account data; spaces sort by it,
 * byte-wise, unordered ones last (by room ID). Orders are printable ASCII, at most 50 characters.
 */
object SpaceOrder {
    const val TYPE = "org.matrix.msc3230.space_order"

    private const val MIN = 0x21 // '!': no spaces, some clients trim them
    private const val MAX = 0x7E // '~'
    private const val MAX_LENGTH = 50

    /**
     * The orders to write so the spaces come out as [ids], given what they carry now ([current]).
     * Moving one space between two ordered neighbours writes only that space; anything else
     * renumbers them all.
     */
    fun writes(
        ids: List<String>,
        current: Map<String, String?>,
        moved: String,
    ): Map<String, String> {
        val at = ids.indexOf(moved)
        val others = ids.filter { it != moved }
        if (at >= 0 && others.all { current[it] != null } && increasing(others.map { current.getValue(it)!! })) {
            val before = ids.getOrNull(at - 1)?.let { current[it] }
            val after = ids.getOrNull(at + 1)?.let { current[it] }
            between(before, after)?.let { return mapOf(moved to it) }
        }
        return spread(ids.size).let { keys -> ids.indices.associate { ids[it] to keys[it] } }
    }

    /** A string strictly between [low] and [high] (null: unbounded), or null if none fits. */
    fun between(
        low: String?,
        high: String?,
    ): String? {
        if (low != null && high != null && low >= high) return null
        val out = StringBuilder()
        var bounded = high != null
        var i = 0
        while (out.length < MAX_LENGTH) {
            val lo = low?.getOrNull(i)?.code ?: (MIN - 1)
            if (bounded && i >= high!!.length) return null
            val hi = if (bounded) high!![i].code else MAX + 1
            when {
                hi - lo > 1 -> {
                    return out.append((lo + (hi - lo) / 2).toChar()).toString()
                }

                hi == lo -> {
                    out.append(lo.toChar())
                }

                lo < MIN -> {
                    // low has ended, high is at the floor: go deeper
                    out.append(hi.toChar())
                }

                else -> {
                    out.append(lo.toChar())
                    bounded = false
                }
            }
            i++
        }
        return null
    }

    /** [n] increasing two-character orders, evenly spread, leaving room on either side. */
    fun spread(n: Int): List<String> {
        val base = MAX - MIN + 1
        val span = base * base
        return (1..n).map { k ->
            val v = (k.toLong() * span / (n + 1)).toInt()
            "${(MIN + v / base).toChar()}${(MIN + v % base).toChar()}"
        }
    }

    private fun increasing(orders: List<String>) = orders.zipWithNext().all { (a, b) -> a < b }
}
