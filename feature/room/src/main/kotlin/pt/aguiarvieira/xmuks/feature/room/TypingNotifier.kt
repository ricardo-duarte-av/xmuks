package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * While [draft] has text, tells the room we're typing (renewed every few seconds, like gomuks
 * web); stops as soon as it's empty, or when [stop] is called (sent, room left).
 */
internal class TypingNotifier(
    private val scope: CoroutineScope,
    draft: TextFieldState,
    private val setTyping: suspend (timeoutMs: Int) -> Unit,
) {
    private var sentAt = 0L

    /** A typing notification is live. */
    val active: Boolean get() = sentAt != 0L

    init {
        scope.launch {
            snapshotFlow { draft.text.toString() }.collect { text ->
                val now = System.currentTimeMillis()
                when {
                    text.isEmpty() -> {
                        stop()
                    }

                    now - sentAt > RENEW_MS -> {
                        sentAt = now
                        setTyping(TIMEOUT_MS)
                    }
                }
            }
        }
    }

    fun stop() {
        if (!active) return
        sentAt = 0L
        scope.launch { setTyping(0) }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val RENEW_MS = 5_000L
    }
}
