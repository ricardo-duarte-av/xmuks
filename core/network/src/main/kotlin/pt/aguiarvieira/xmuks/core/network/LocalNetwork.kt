package pt.aguiarvieira.xmuks.core.network

import okhttp3.HttpUrl
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Whether [address] is on the local network (private, link-local, CGNAT or ULA ranges): from
 * Android 17 on, reaching one needs ACCESS_LOCAL_NETWORK. Loopback has its own rules and isn't.
 */
fun isLocalNetwork(address: InetAddress): Boolean {
    if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
    val bytes = address.address
    return when (address) {
        // 100.64.0.0/10: carrier-grade NAT, and what Tailscale-style VPNs hand out.
        is Inet4Address -> bytes[0].toInt() == CGNAT_FIRST && (bytes[1].toInt() and CGNAT_MASK) == CGNAT_SECOND

        // fc00::/7: unique local addresses.
        is Inet6Address -> (bytes[0].toInt() and ULA_MASK) == ULA_PREFIX

        else -> false
    }
}

/** Whether this URL's server is on the local network. Blocking: resolves the name. Unresolvable → false. */
fun HttpUrl.isOnLocalNetwork(): Boolean =
    host.endsWith(".local") ||
        runCatching { InetAddress.getAllByName(host).any(::isLocalNetwork) }.getOrDefault(false)

private const val CGNAT_FIRST = 100
private const val CGNAT_MASK = 0xC0
private const val CGNAT_SECOND = 0x40
private const val ULA_MASK = 0xFE
private const val ULA_PREFIX = 0xFC
