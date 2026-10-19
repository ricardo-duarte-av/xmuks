package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.network.ExecResult

/**
 * Which network bridged users (puppets) are on, from their global profile (`get_profile`): bridges
 * put `com.beeper.bridge.network` there, not always in each room's member event. Asked once per
 * user for the app's lifetime, across rooms; users whose profile names none map to null.
 */
class BridgedUsers(
    private val exec: ExecClient,
    private val scope: CoroutineScope,
) {
    private val _networks = MutableStateFlow<Map<String, String?>>(emptyMap())
    val networks: StateFlow<Map<String, String?>> = _networks.asStateFlow()

    private val requested = HashSet<String>()
    private val lock = Mutex()

    /**
     * Fetches, in the background, the profiles of those of [senders] not asked about yet whose
     * [known] member profile names no network (never ours, [me]).
     */
    fun request(
        senders: Collection<String>,
        known: Map<String, MemberProfile>,
        me: String?,
    ) {
        val wanted = senders.filterTo(HashSet()) { it != me && known[it]?.network == null }
        if (wanted.isEmpty()) return
        scope.launch {
            val batch = lock.withLock { wanted.filter(requested::add) }
            batch.forEach { userId ->
                val params = buildJsonObject { put("user_id", JsonPrimitive(userId)) }
                val result = exec.exec("get_profile", params, ExecMode.Read) as? ExecResult.Ok
                val network = ((result?.data as? JsonObject)?.obj("profile"))?.let(::bridgeNetworkOf)
                _networks.value += userId to network
            }
        }
    }
}

/** [profiles] with the networks [BridgedUsers] found for those whose member event names none. */
internal fun withNetworks(
    profiles: Map<String, MemberProfile>,
    networks: Map<String, String?>,
): Map<String, MemberProfile> =
    if (networks.isEmpty()) {
        profiles
    } else {
        profiles.mapValues { (userId, profile) ->
            if (profile.network != null) profile else networks[userId]?.let { profile.copy(network = it) } ?: profile
        }
    }
