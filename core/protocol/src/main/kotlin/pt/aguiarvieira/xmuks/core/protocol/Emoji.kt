package pt.aguiarvieira.xmuks.core.protocol

/**
 * The emoji a name starts with, whole (skin tones, ZWJ families, flags, keycaps and all), or null
 * when it starts with anything else. Leading spaces are skipped. Avatars without a picture show it
 * in place of the name's first letter: "🐈 Cats" gets a cat, not a "C".
 *
 * Ready to draw: a lone emoji gets the emoji variation selector (U+FE0F), so the older symbols
 * (☀, ✈, ☎), which default to a black-and-white text glyph, are drawn as the colour emoji too.
 */
fun leadingEmoji(name: String): String? {
    val text = name.trimStart()
    if (text.isEmpty()) return null
    val first = text.codePointAt(0)
    var end = Character.charCount(first)
    // A regional indicator pairs with the next one into a flag.
    if (first in REGIONAL_INDICATORS) {
        if (end < text.length && text.codePointAt(end) in REGIONAL_INDICATORS) {
            end += Character.charCount(text.codePointAt(end))
        }
        return text.substring(0, end)
    }
    end = extend(text, end)
    val cluster = text.substring(0, end)
    val presented = VARIATION_EMOJI in cluster || KEYCAP in cluster
    if (!isEmojiStart(first, presented)) return null
    return if (presented || cluster.codePointCount(0, cluster.length) > 1) cluster else cluster + VARIATION_EMOJI
}

/** Past the modifiers, joiners and tags that belong to the emoji ending at [from]. */
private fun extend(
    text: String,
    from: Int,
): Int {
    var end = from
    while (end < text.length) {
        val cp = text.codePointAt(end)
        val size = Character.charCount(cp)
        end +=
            when {
                cp == VARIATION_EMOJI.code || cp == KEYCAP.code || cp in SKIN_TONES || cp in TAGS -> {
                    size
                }

                // A joiner glues on the next emoji (👩‍💻), with its own modifiers.
                cp == ZWJ && end + size < text.length -> {
                    size + Character.charCount(text.codePointAt(end + size))
                }

                else -> {
                    return end
                }
            }
    }
    return end
}

/**
 * Whether [cp] starts an emoji. Pictographs are; symbols from the older blocks (☀, ❤, ⌚) are too,
 * as they're pictures, not letters. Digits, `#` and `*` only count as a keycap (1️⃣).
 */
private fun isEmojiStart(
    cp: Int,
    presented: Boolean,
): Boolean =
    when (cp) {
        in PICTOGRAPHS, in SYMBOLS, in MISC_TECHNICAL, in STARS -> true
        in TEXT_DEFAULT -> presented
        else -> presented && (cp == '#'.code || cp == '*'.code || cp in '0'.code..'9'.code)
    }

private val PICTOGRAPHS = 0x1F000..0x1FAFF
private val SYMBOLS = 0x2600..0x27BF
private val MISC_TECHNICAL = 0x2300..0x23FF
private val STARS = 0x2B00..0x2BFF
private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
private val SKIN_TONES = 0x1F3FB..0x1F3FF
private val TAGS = 0xE0020..0xE007F

/** Symbols drawn as text unless asked for as emoji: ©, ®, ™, ‼, ⁉, arrows, ℹ, 〰, 〽, ㊗, ㊙. */
private val TEXT_DEFAULT =
    setOf(0xA9, 0xAE, 0x203C, 0x2049, 0x2122, 0x2139, 0x3030, 0x303D, 0x3297, 0x3299) + (0x2194..0x21AA)

private const val VARIATION_EMOJI = '️'
private const val KEYCAP = '⃣'
private const val ZWJ = 0x200D
