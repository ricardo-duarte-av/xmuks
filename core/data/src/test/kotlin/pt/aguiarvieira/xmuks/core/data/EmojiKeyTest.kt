package pt.aguiarvieira.xmuks.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.emoji.isEmojiKey

@RunWith(AndroidJUnit4::class)
class EmojiKeyTest {
    @Test
    fun `emoji of every shape count`() {
        listOf("😀", "👍🏽", "❤️", "🇵🇹", "👨‍👩‍👧", "🏳️‍🌈", "mxc://s/shiggy").forEach {
            assertTrue(it, isEmojiKey(it))
        }
    }

    @Test
    fun `text reactions don't`() {
        listOf("teste", "hello", "+1", "a", "😀😀", "lol 😀", "", "1", "#").forEach {
            assertFalse(it, isEmojiKey(it))
        }
    }
}
