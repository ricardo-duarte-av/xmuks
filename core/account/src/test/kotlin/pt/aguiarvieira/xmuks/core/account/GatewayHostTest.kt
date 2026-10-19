package pt.aguiarvieira.xmuks.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayHostTest {
    @Test
    fun `what's typed becomes the bare host gomuks compares`() {
        assertEquals("push.aguiarvieira.pt", gatewayHost("https://push.aguiarvieira.pt/"))
        assertEquals("push.aguiarvieira.pt", gatewayHost("  Push.AguiarVieira.pt  "))
        assertEquals("push.example.org", gatewayHost("http://push.example.org/_gomuks/push/fcm"))
    }

    @Test
    fun `blank or gomuks' own gateway means the default`() {
        assertNull(gatewayHost(""))
        assertNull(gatewayHost("   "))
        assertNull(gatewayHost("https://push.gomuks.app"))
    }
}
