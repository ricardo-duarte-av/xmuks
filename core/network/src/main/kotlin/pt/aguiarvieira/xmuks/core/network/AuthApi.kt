package pt.aguiarvieira.xmuks.core.network

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import java.io.IOException
import okhttp3.Credentials as BasicAuth

sealed interface AuthResult {
    data class Success(
        val token: String,
    ) : AuthResult

    /** Username/password rejected. */
    data object BadCredentials : AuthResult

    /** Refused to send a password over plain HTTP. */
    data object InsecureTransport : AuthResult

    data class ServerError(
        val code: Int,
        val message: String,
    ) : AuthResult

    data class NetworkError(
        val cause: IOException,
    ) : AuthResult
}

/**
 * `POST /_gomuks/auth?output=json` with HTTP Basic, returning the session token gomuks would
 * otherwise put in the `gomuks_auth` cookie (valid 7 days). [http] must not carry [AuthInterceptor].
 */
open class AuthApi(
    private val http: OkHttpClient,
) {
    open fun login(credentials: Credentials): AuthResult {
        if (!credentials.isSecure) return AuthResult.InsecureTransport
        val request =
            Request
                .Builder()
                .url(
                    credentials.serverUrl
                        .gomuks("auth")
                        .newBuilder()
                        .addQueryParameter("output", "json")
                        .addQueryParameter("no_prompt", "true")
                        .build(),
                ).header("Authorization", BasicAuth.basic(credentials.username, credentials.password, Charsets.UTF_8))
                .post(ByteArray(0).toRequestBody())
                .build()
        return try {
            http.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful -> {
                        val token =
                            runCatching {
                                GomuksJson
                                    .parseToJsonElement(body)
                                    .jsonObject["token"]
                                    ?.jsonPrimitive
                                    ?.content
                            }.getOrNull()
                        if (token.isNullOrEmpty()) {
                            AuthResult.ServerError(response.code, "No token in /auth response")
                        } else {
                            AuthResult.Success(token)
                        }
                    }

                    response.code == HTTP_UNAUTHORIZED -> {
                        AuthResult.BadCredentials
                    }

                    else -> {
                        AuthResult.ServerError(response.code, body.take(ERROR_BODY_CHARS))
                    }
                }
            }
        } catch (e: IOException) {
            AuthResult.NetworkError(e)
        }
    }
}

internal const val HTTP_UNAUTHORIZED = 401
internal const val ERROR_BODY_CHARS = 200
