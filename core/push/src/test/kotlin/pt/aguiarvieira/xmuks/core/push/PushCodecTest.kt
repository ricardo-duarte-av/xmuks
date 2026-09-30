package pt.aguiarvieira.xmuks.core.push

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
    fun `wrong key, tampering or garbage give nothing`() {
        assertNull(PushCodec.open(seal(sample, ByteArray(32)), key))
        val sealed = Base64.getDecoder().decode(seal(sample))
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertNull(PushCodec.open(Base64.getEncoder().encodeToString(sealed), key))
        assertNull(PushCodec.open("not base64 !!", key))
    }
}
