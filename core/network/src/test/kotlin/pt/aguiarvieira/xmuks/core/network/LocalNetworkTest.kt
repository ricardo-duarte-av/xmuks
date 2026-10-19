package pt.aguiarvieira.xmuks.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LocalNetworkTest {
    private fun local(ip: String) = isLocalNetwork(InetAddress.getByName(ip))

    @Test
    fun `private, link-local, CGNAT and ULA addresses are local`() {
        listOf(
            "10.0.0.1",
            "172.16.5.4",
            "192.168.1.10",
            "169.254.1.1",
            "100.64.0.1",
            "100.127.255.254",
            "fe80::1",
            "fd12:3456::1",
        ).forEach { assertTrue(it, local(it)) }
    }

    @Test
    fun `public and loopback addresses are not`() {
        val elsewhere = listOf("1.1.1.1", "172.32.0.1", "100.128.0.1", "127.0.0.1", "::1", "2001:4860:4860::8888")
        elsewhere.forEach { assertFalse(it, local(it)) }
    }

    @Test
    fun `an IP-literal URL is judged without DNS`() {
        assertTrue("https://192.168.1.2:8443/".toHttpUrl().isOnLocalNetwork())
        assertFalse("https://8.8.8.8/".toHttpUrl().isOnLocalNetwork())
    }

    @Test
    fun `mDNS names count as local`() {
        assertTrue("https://gomuks.local/".toHttpUrl().isOnLocalNetwork())
    }
}
