package pt.aguiarvieira.xmuks.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PushCodecTest {
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    /** As gomuks' encryptPush: iv ‖ AES-GCM(ciphertext+tag), then base64 by the gateway. */
    private fun seal(
        plain: String,
        with: ByteArray = key,
    ): String {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(with, "AES"), GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
    }

    private val sample =
        """
        {"messages": [{"timestamp": 1790683133314, "event_id": "${'$'}e", "event_rowid": 5, "room_id": "!r:s",
          "room_name": "Alice", "room_avatar": "_gomuks/media/s/a?encrypted=false&fallback=A",
          "sender": {"id": "@alice:s", "name": "Alice"}, "self": {"id": "@me:s", "name": "Me"},
          "text": "hi", "sound": true, "extra": 1}],
         "dismiss": [{"room_id": "!old:s", "read_up_to": "${'$'}x", "ts": 1}],
         "image_auth": "tok"}
        """

    @Test
    fun `opens what gomuks seals`() {
        val payload = PushCodec.open(seal(sample), key)!!
        val message = payload.messages.single()
        assertEquals("Alice", message.roomName)
        assertEquals("@alice:s", message.sender.id)
        assertTrue(message.sound)
        assertEquals("!old:s", payload.dismiss.single().roomId)
        assertEquals("tok", payload.imageAuth)
        assertEquals(false, message.isDm) // older gomuks: no is_dm at all
    }

    @Test
    fun `newer gomuks marks DMs`() {
        val payload = PushCodec.open(seal(sample.replace("\"room_name\": \"Alice\",", "\"room_name\": \"Alice\", \"is_dm\": true,")), key)!!
        assertTrue(payload.messages.single().isDm)
    }

    @Test
    fun `a per-message profile shows as profile via sender, apart from the sender`() {
        val profile = "\"per_message_profile\": {\"id\": \"cat\", \"name\": \"Cat\", \"avatar\": \"_gomuks/media/c\"},"
        val sealed = seal(sample.replace("\"text\": \"hi\",", "$profile \"text\": \"hi\","))
        val message = PushCodec.open(sealed, key)!!.messages.single()
        assertEquals("Cat via Alice", message.author.name)
        assertEquals("_gomuks/media/c", message.author.avatar)
        assertTrue(message.author.id != message.sender.id)
        assertEquals("Cat", message.authorName)
    }

    @Test
    fun `without a per-message profile the sender is the author`() {
        val message = PushCodec.open(seal(sample), key)!!.messages.single()
        assertEquals(message.sender, message.author)
        assertEquals("Alice", message.authorName)
    }

    @Test
    fun `wrong key, tampering or garbage give nothing`() {
        assertNull(PushCodec.open(seal(sample, ByteArray(32)), key))
        val sealed = Base64.getDecoder().decode(seal(sample))
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertNull(PushCodec.open(Base64.getEncoder().encodeToString(sealed), key))
        assertNull(PushCodec.open("not base64 !!", key))
    }
}
