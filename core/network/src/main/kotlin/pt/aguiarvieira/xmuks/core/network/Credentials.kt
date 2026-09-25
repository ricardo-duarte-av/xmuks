package pt.aguiarvieira.xmuks.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where the gomuks backend lives and how to log in to it (its web username/password). */
data class Credentials(
    val serverUrl: HttpUrl,
    val username: String,
    val password: String,
) {
    /** HTTP Basic sends the password in the clear on plain HTTP; only ever do that over TLS. */
    val isSecure: Boolean get() = serverUrl.isHttps

    override fun toString() = "Credentials(serverUrl=$serverUrl, username=$username, password=***)"
}

/** Everything under `/_gomuks/` on [base]. */
fun HttpUrl.gomuks(vararg segments: String): HttpUrl =
    newBuilder()
        .addPathSegment("_gomuks")
        .apply { segments.forEach(::addPathSegments) }
        .build()

/**
 * Parses what a user types into the server field: a scheme is optional (https is assumed), and a
 * trailing path is kept so gomuks can live under a prefix behind a reverse proxy.
 */
fun parseServerUrl(input: String): HttpUrl? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    return withScheme.toHttpUrlOrNull()
}
