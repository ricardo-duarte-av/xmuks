package pt.aguiarvieira.xmuks.core.call.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where call audio can go. */
enum class RouteKind { Earpiece, Speaker, Bluetooth, WiredHeadset, Other }

/** One place call audio can be played (and picked up): as Telecom reports it. */
data class AudioRoute(
    val id: String,
    val name: String,
    val kind: RouteKind,
)

/**
 * The call's audio routes, shared between the service that owns the Telecom call (which reports
 * them and carries out changes) and the call screen (which shows them and asks for changes).
 */
class CallAudio {
    private val mutableRoutes = MutableStateFlow<List<AudioRoute>>(emptyList())
    val routes: StateFlow<List<AudioRoute>> = mutableRoutes.asStateFlow()

    private val mutableCurrent = MutableStateFlow<AudioRoute?>(null)
    val current: StateFlow<AudioRoute?> = mutableCurrent.asStateFlow()

    @Volatile private var changer: ((AudioRoute) -> Unit)? = null

    /** Asks for audio to go to [route]; nothing happens while no call owns the audio. */
    fun select(route: AudioRoute) {
        changer?.invoke(route)
    }

    internal fun attach(change: (AudioRoute) -> Unit) {
        changer = change
    }

    internal fun update(
        routes: List<AudioRoute>? = null,
        current: AudioRoute? = null,
    ) {
        routes?.let { mutableRoutes.value = it }
        current?.let { mutableCurrent.value = it }
    }

    internal fun detach() {
        changer = null
        mutableRoutes.value = emptyList()
        mutableCurrent.value = null
    }
}
