package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.MessageContent

/**
 * Audio: a play button, the waveform when the sender gave one (voice messages do), otherwise the
 * file's name, and the duration.
 */
@Composable
internal fun AudioCard(
    audio: MessageContent.Audio,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(color.copy(alpha = BUTTON_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_play),
                stringResource(R.string.audio),
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val waveform = audio.waveform
            if (waveform != null) {
                Waveform(waveform, color, Modifier.width(WAVEFORM_WIDTH).height(WAVEFORM_HEIGHT))
            } else {
                Text(
                    audio.name ?: stringResource(R.string.audio),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            audio.durationMs?.let {
                Text(clock(it), color = color.copy(alpha = LABEL_ALPHA), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Bars of the loudness, resampled to fit; a floor keeps silence visible as a line. */
@Composable
private fun Waveform(
    samples: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val bars = remember(samples) { resample(samples, BARS) }
    Canvas(modifier) {
        val step = size.width / bars.size
        val barWidth = step * BAR_FILL
        bars.forEachIndexed { i, level ->
            val h = size.height * level.coerceIn(MIN_BAR, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * step, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/** [samples] averaged into [count] buckets (or kept, when there are fewer). */
internal fun resample(
    samples: List<Float>,
    count: Int,
): List<Float> {
    if (samples.size <= count) return samples
    return List(count) { i ->
        val from = i * samples.size / count
        val to = ((i + 1) * samples.size / count).coerceAtLeast(from + 1)
        samples.subList(from, to).average().toFloat()
    }
}

private fun clock(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private const val BARS = 40
private const val BAR_FILL = 0.6f
private const val MIN_BAR = 0.08f
private const val BUTTON_ALPHA = 0.15f
private const val LABEL_ALPHA = 0.7f
private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private val WAVEFORM_WIDTH = 160.dp
private val WAVEFORM_HEIGHT = 28.dp
