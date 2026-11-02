package pt.aguiarvieira.xmuks.core.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.data.rooms.OwnProfile
import pt.aguiarvieira.xmuks.core.data.rooms.Unread
import java.util.concurrent.ConcurrentHashMap

/** A signed-in account as the account switcher shows it. */
data class AccountSummary(
    val id: String,
    /** Its Matrix profile; null until its database knows it. */
    val profile: OwnProfile?,
    /** Its gomuks login, `user · host`: what tells two of the same Matrix user apart. */
    val login: String,
    /** Unread across its rooms (as its Chats tab counts them). */
    val unread: Unread,
)

/** Sets an account's graph going when it's built (its stream, shortcuts, notifications…). */
fun interface AccountStarter {
    fun start(graph: AccountGraph)
}

/**
 * The accounts' graphs: one per account, built on first use and kept. [current] is the account on
 * screen; with none signed in, the one the next sign-in will use (so what's built before logging
 * in is what that account then runs on, as it always was).
 */
class Accounts(
    val registry: AccountRegistry,
    private val app: AppDependencies,
    private val starters: Set<AccountStarter>,
    private val scope: CoroutineScope,
) {
    private val graphs = ConcurrentHashMap<String, AccountGraph>()

    /** The account on screen; null when none is signed in. */
    val active: StateFlow<String?> = registry.active

    fun current(): AccountGraph = graph(registry.active.value ?: registry.nextId())

    /** [id]'s graph, built (and started) the first time it's asked for. */
    fun graph(id: String): AccountGraph {
        graphs[id]?.let { return it }
        val built =
            synchronized(graphs) {
                graphs[id]?.let { return it }
                val isActive =
                    registry.active.map { it == id }.stateIn(scope, SharingStarted.Eagerly, registry.active.value == id)
                AccountGraph(id, registry.store(id), isActive, app).also { graphs[id] = it }
            }
        starters.forEach { it.start(built) }
        return built
    }

    /** Shows [id] (signed in already): its screens take over from the shown account's. */
    fun switchTo(id: String) {
        scope.launch { registry.activate(id) }
    }

    /** The signed-in accounts as the switcher shows them, in the order they were added. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun summaries(): Flow<List<AccountSummary>> =
        registry.ids.flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList()) else combine(ids.map { summary(graph(it)) }) { it.toList() }
        }

    private fun summary(graph: AccountGraph): Flow<AccountSummary> {
        val rooms = graph.roomListRepository
        return combine(rooms.ownProfile(), rooms.tabBadges()) { profile, badges ->
            val login =
                graph.store
                    .credentials()
                    ?.let { "${it.username} · ${it.serverUrl.host}" }
                    .orEmpty()
            AccountSummary(graph.id, profile, login, badges.chats)
        }
    }

    /** Every signed-in account's graph (built if need be), in the order they were added. */
    fun signedIn(): List<AccountGraph> = registry.ids.value.map(::graph)

    /** Keeps the shown account going: built and started as soon as it's switched to. */
    fun start() {
        current()
        scope.launch { registry.active.collect { current() } }
    }
}
