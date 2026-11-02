package pt.aguiarvieira.xmuks.core.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.account.AccountRegistry
import java.util.concurrent.ConcurrentHashMap

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

    /** Keeps the shown account going: built and started as soon as it's switched to. */
    fun start() {
        current()
        scope.launch { registry.active.collect { current() } }
    }
}
