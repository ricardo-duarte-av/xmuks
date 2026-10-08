package pt.aguiarvieira.xmuks.core.call.media

import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pt.aguiarvieira.xmuks.core.call.signalling.RtcApi
import pt.aguiarvieira.xmuks.core.network.await
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson
import pt.aguiarvieira.xmuks.core.protocol.rtc.CallMemberships
import pt.aguiarvieira.xmuks.core.protocol.rtc.RtcTransport
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.CoroutineContext

/** Where to connect and with what: an SFU WebSocket URL and a LiveKit access token for it. */
data class SfuAccess(
    val url: String,
    val jwt: String,
) {
    /** The participant identity the token grants (`sub`), for matching our own tracks. */
    val identity: String? get() = claims()?.get("sub")?.let { (it as? JsonPrimitive)?.contentOrNull }

    private fun claims(): JsonObject? =
        runCatching {
            val payload = jwt.split('.')[1]
            GomuksJson.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload))).jsonObject
        }.getOrNull()
}

/** Who asks for an SFU token: our side of the membership. */
data class TokenSubject(
    val userId: String,
    val deviceId: String,
    val memberId: String,
)

/**
 * Gets LiveKit tokens for a transport, the way Element Call does, plus the MSC4195 homeserver route:
 *
 *  - `m.livekit` transports (MSC4195: they carry the SFU `url` itself) go through gomuks'
 *    `rtc_livekit_get_token`, i.e. the homeserver issues the token.
 *  - `livekit` transports (today's: a `livekit_service_url` of an lk-jwt-service) take an OpenID
 *    token from gomuks to that service: `/sfu/get` for legacy members (identity `@user:DEVICE`),
 *    `/get_token` for sticky ones (identity = hash of user, device and member id).
 */
class SfuTokens(
    private val api: RtcApi,
    private val http: OkHttpClient,
    private val io: CoroutineContext,
) {
    suspend fun access(
        transport: RtcTransport,
        roomId: String,
        subject: TokenSubject,
        legacy: Boolean,
        delegation: DelayDelegation? = null,
    ): Result<SfuAccess> {
        val sfuUrl = transport.raw["url"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (transport.type == MSC4195_TRANSPORT && sfuUrl != null) {
            return api
                .livekitToken(sfuUrl, roomId, CallMemberships.ROOM_SLOT_ID, subject.memberId)
                .map { SfuAccess(sfuUrl, it) }
        }
        val service =
            transport.livekitServiceUrl?.trimEnd('/')
                ?: return Result.failure(IOException("Transport without a service URL"))
        val openId = api.openIdToken().getOrElse { return Result.failure(it) }
        return if (legacy) {
            sfuGet(service, roomId, subject, openId, delegation)
        } else {
            getToken(service, roomId, subject, openId, delegation)
        }
    }

    private suspend fun sfuGet(
        service: String,
        roomId: String,
        subject: TokenSubject,
        openId: JsonObject,
        delegation: DelayDelegation?,
    ): Result<SfuAccess> {
        fun body(withDelay: Boolean) =
            buildJsonObject {
                put("room", roomId)
                put("openid_token", openId)
                put("device_id", subject.deviceId)
                if (withDelay && delegation != null) delegation.into(this)
            }
        val first = post("$service/sfu/get", body(withDelay = true))
        // Services predating delegation reject the extra fields as M_BAD_JSON.
        if (first.isFailure && delegation != null &&
            (first.exceptionOrNull() as? SfuHttpException)?.status == HTTP_BAD_REQUEST
        ) {
            return post("$service/sfu/get", body(withDelay = false))
        }
        return first
    }

    private suspend fun getToken(
        service: String,
        roomId: String,
        subject: TokenSubject,
        openId: JsonObject,
        delegation: DelayDelegation?,
    ): Result<SfuAccess> {
        val body =
            buildJsonObject {
                put("room_id", roomId)
                put("slot_id", CallMemberships.ROOM_SLOT_ID)
                put("openid_token", openId)
                put(
                    "member",
                    buildJsonObject {
                        put("id", subject.memberId)
                        put("claimed_user_id", subject.userId)
                        put("claimed_device_id", subject.deviceId)
                    },
                )
                delegation?.into(this)
            }
        return post("$service/get_token", body)
    }

    private suspend fun post(
        url: String,
        body: JsonObject,
    ): Result<SfuAccess> =
        withContext(io) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .post(body.toString().toRequestBody(JSON))
                        .build()
                http.newCall(request).await().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) throw SfuHttpException(response.code, text.take(ERROR_CHARS))
                    val obj = GomuksJson.parseToJsonElement(text).jsonObject
                    SfuAccess(
                        url = (obj["url"] as JsonPrimitive).content,
                        jwt = (obj["jwt"] as JsonPrimitive).content,
                    )
                }
            }
        }

    private companion object {
        const val MSC4195_TRANSPORT = "m.livekit"
        const val HTTP_BAD_REQUEST = 400
        const val ERROR_CHARS = 300
        val JSON = "application/json".toMediaType()
    }
}

/**
 * Hands our delayed leave to the JWT service, which keeps restarting it for as long as we're
 * connected to the SFU — so a call survives the app being frozen in the background.
 */
data class DelayDelegation(
    val delayId: String,
    val timeoutMs: Long,
    val homeserverUrl: String,
) {
    fun into(builder: kotlinx.serialization.json.JsonObjectBuilder) {
        builder.put("delay_id", delayId)
        builder.put("delay_timeout", timeoutMs)
        builder.put("delay_cs_api_url", homeserverUrl)
    }
}

class SfuHttpException(
    val status: Int,
    body: String,
) : IOException("SFU token request failed ($status): $body")
