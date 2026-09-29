package pt.aguiarvieira.xmuks.feature.room

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records a voice message: Opus in Ogg (what Matrix clients send and play), mono, speech bitrate.
 * One recording at a time; [stop] returns the file, [cancel] throws it away.
 */
internal class VoiceRecorder(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val recording: Boolean get() = recorder != null

    /** Milliseconds since [start]. */
    val elapsedMs: Long get() = if (recording) System.currentTimeMillis() - startedAt else 0L

    /** The loudest level since the last call, 0..1: drives the level meter. */
    fun level(): Float = (recorder?.maxAmplitude ?: 0).toFloat() / MAX_AMPLITUDE

    fun start() {
        cancel()
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val out = File(dir, "voice_$stamp.ogg")
        recorder =
            MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioChannels(1)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setOutputFile(out)
                prepare()
                start()
            }
        file = out
        startedAt = System.currentTimeMillis()
    }

    /** The finished recording, or null when there was nothing (or it failed). */
    fun stop(): File? {
        val done = recorder ?: return null
        recorder = null
        val ok = runCatching { done.stop() }.isSuccess
        done.release()
        return file?.takeIf { ok && it.length() > 0 }.also { if (it == null) file?.delete() }
    }

    fun cancel() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
        file?.delete()
        file = null
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val BIT_RATE = 32_000
        const val MAX_AMPLITUDE = 32_767f
    }
}
