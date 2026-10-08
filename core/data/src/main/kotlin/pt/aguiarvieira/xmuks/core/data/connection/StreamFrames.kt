package pt.aguiarvieira.xmuks.core.data.connection

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pt.aguiarvieira.xmuks.core.protocol.GomuksFrame

/**
 * Every frame off the stream, for listeners outside this module (calls watch memberships and
 * to-device keys). Frames arriving while nobody listens are simply not kept.
 */
class StreamFrames {
    private val mutable = MutableSharedFlow<GomuksFrame>(extraBufferCapacity = BUFFER)

    val frames: SharedFlow<GomuksFrame> = mutable.asSharedFlow()

    fun accept(frame: GomuksFrame) {
        mutable.tryEmit(frame)
    }

    private companion object {
        /** Room for a burst of syncs while a slow listener catches up. */
        const val BUFFER = 256
    }
}
