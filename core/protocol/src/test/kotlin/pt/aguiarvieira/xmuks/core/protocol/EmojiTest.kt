package pt.aguiarvieira.xmuks.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiTest {
    @Test
    fun `a lone emoji is asked for in colour`() = assertEquals("🐈\uFE0F", leadingEmoji("🐈 Cats"))

    @Test
    fun `leading spaces are skipped`() = assertEquals("🎶\uFE0F", leadingEmoji("  🎶 Jellyfin stream room"))

    @Test
    fun `an emoji with a variation selector`() = assertEquals("🏴‍☠️", leadingEmoji("🏴‍☠️ CrackWatch"))

    @Test
    fun `a symbol drawn as emoji`() = assertEquals("❤️", leadingEmoji("❤️ Friends"))

    @Test
    fun `an older symbol without a selector still counts`() = assertEquals("☕\uFE0F", leadingEmoji("☕ Coffee"))

    @Test
    fun `a skin tone stays with its hand`() = assertEquals("👋🏽", leadingEmoji("👋🏽 hi"))

    @Test
    fun `a joined family is one emoji`() = assertEquals("👩‍💻", leadingEmoji("👩‍💻 Devs"))

    @Test
    fun `a flag is both its letters`() = assertEquals("🇵🇹", leadingEmoji("🇵🇹 Portugal"))

    @Test
    fun `a keycap`() = assertEquals("1️⃣", leadingEmoji("1️⃣ First"))

    @Test
    fun `a digit alone is not an emoji`() = assertNull(leadingEmoji("1 Room"))

    @Test
    fun `a letter is not an emoji`() = assertNull(leadingEmoji("Matrix HQ"))

    @Test
    fun `a sigil is not an emoji`() = assertNull(leadingEmoji("#gomuks"))

    @Test
    fun `a copyright sign without a selector is text`() = assertNull(leadingEmoji("© Legal"))

    @Test
    fun `an emoji later in the name is not used`() = assertNull(leadingEmoji("Cats 🐈"))

    @Test
    fun `empty names have none`() = assertNull(leadingEmoji("   "))
}
