package pt.aguiarvieira.xmuks.core.data.account

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import pt.aguiarvieira.xmuks.core.data.commands.MatrixLink
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
    /**
     * Its gomuks, `host` (with the path, when one host serves several): a gomuks has one user, so
     * this and the Matrix ID are enough to tell accounts apart.
     */
    val server: String = login,
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

    /** The accounts whose starters have run (once each). */
    private val started = ConcurrentHashMap.newKeySet<String>()

    /** The account on screen; null when none is signed in. */
    val active: StateFlow<String?> = registry.active

    fun current(): AccountGraph = graph(registry.active.value ?: registry.nextId())

    /**
     * [id]'s graph, built and started (its stream, outbox, shortcuts, contacts…) the first time
     * it's asked for: for an account that is used, not just looked into ([built]).
     */
    fun graph(id: String): AccountGraph {
        val graph = built(id)
        if (started.add(id)) starters.forEach { it.start(graph) }
        return graph
    }

    /**
     * [id]'s graph, built if need be but not started: enough to read what it holds (its push key,
     * its user, its rooms). A push or a lookup must not set every account going: each start opens
     * databases, a Contacts sync, shortcut updates… [graph] starts it when it's actually used.
     */
    fun built(id: String): AccountGraph {
        graphs[id]?.let { return it }
        return synchronized(graphs) {
            graphs[id]?.let { return it }
            val isActive =
                registry.active.map { it == id }.stateIn(scope, SharingStarted.Eagerly, registry.active.value == id)
            AccountGraph(id, registry.store(id), isActive, app, ::userIds).also { graphs[id] = it }
        }
    }

    /** Shows [id] (signed in already): its screens take over from the shown account's. */
    fun switchTo(
        id: String,
        swiped: Boolean = false,
    ) {
        swipe = if (swiped) registry.active.value to id else null
        scope.launch { registry.activate(id) }
    }

    /**
     * The last switch, as (from, to), when it was a swipe on the room list: that already brought the
     * account's screen in, so the switch itself isn't animated. Null after any other.
     */
    @Volatile var swipe: Pair<String?, String>? = null
        private set

    /** The signed-in accounts as the switcher shows them, in the order they were added. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun summaries(): Flow<List<AccountSummary>> =
        registry.ids.flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList()) else combine(ids.map { summary(built(it)) }) { it.toList() }
        }

    private fun summary(graph: AccountGraph): Flow<AccountSummary> {
        val rooms = graph.roomListRepository
        return combine(rooms.ownProfile(), rooms.tabBadges()) { profile, badges ->
            val credentials = graph.store.credentials()
            val login = credentials?.let { "${it.username} · ${it.serverUrl.host}" }.orEmpty()
            val server =
                credentials
                    ?.serverUrl
                    ?.let { url -> url.host + url.encodedPath.trimEnd('/') }
                    .orEmpty()
            AccountSummary(graph.id, profile, login, server = server, unread = badges.chats)
        }
    }

    /** Every signed-in account's Matrix user, as its cache knows it (none yet: not listed). */
    private suspend fun userIds(): Set<String> = signedIn().mapNotNull { userOf(it) }.toSet()

    private suspend fun userOf(graph: AccountGraph) =
        graph.database
            .syncDao()
            .meta()
            ?.userId

    /** The signed-in account that is the Matrix user [userId], if any. */
    suspend fun accountOfUser(userId: String): String? = signedIn().firstOrNull { userOf(it) == userId }?.id

    /**
     * The account to open a room link from elsewhere in: the shown one if it's in the room (or the
     * link isn't to a room ID), else another signed-in account that is. Null: the shown one.
     */
    suspend fun accountForLink(uri: String): String? {
        val roomId = MatrixLink.parse(uri)?.id?.takeIf { it.startsWith("!") } ?: return null
        val active = registry.active.value ?: return null

        suspend fun AccountGraph.isIn() = roomListRepository.room(roomId).first() != null
        if (built(active).isIn()) return null
        return signedIn().firstOrNull { it.id != active && it.isIn() }?.id
    }

    /** Every signed-in account's graph (built if need be, not started), in the order they were added. */
    fun signedIn(): List<AccountGraph> = registry.ids.value.map(::built)

    /**
     * Keeps the shown account going once the app is first on screen: built and started then, and
     * as soon as it's switched to. A process woken in the background (a push, the catch-up worker,
     * the watch, Contacts) starts no account for it: each does only what it came for. Main thread.
     */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    owner.lifecycle.removeObserver(this)
                    current()
                    scope.launch { registry.active.collect { current() } }
                }
            },
        )
    }
}
