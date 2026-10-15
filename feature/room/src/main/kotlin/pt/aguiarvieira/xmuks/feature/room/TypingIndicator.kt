package pt.aguiarvieira.xmuks.feature.room

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.Typist
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar
import kotlin.math.PI
import kotlin.math.sin

/**
 * Who's typing, as a row under the newest message: their avatars, overlapping, and a bubble with
 * three dots in a wave and their names. It unfolds from the bottom of the timeline (the list, laid
 * out from the bottom, slides up to make room) and folds away when they stop; avatars pop in as
 * people join, and the names roll over when the list changes. With system animations off, the
 * dots hold still.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TypingIndicator(
    typing: List<Typist>,
    resolver: MediaResolver,
    modifier: Modifier = Modifier,
) {
    // Still shown, as it was, while it folds away.
    var shown by remember { mutableStateOf(typing) }
    if (typing.isNotEmpty()) shown = typing
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = typing.isNotEmpty(),
        modifier = modifier,
        enter =
            expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) +
                slideInVertically(motion.defaultSpatialSpec()) { it / 2 } +
                fadeIn(motion.defaultEffectsSpec()),
        exit =
            shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Top) +
                fadeOut(motion.fastEffectsSpec()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = EDGE, end = EDGE, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Faces(shown.take(MAX_FACES), resolver)
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dots()
                    Spacer(Modifier.width(10.dp))
                    AnimatedContent(
                        targetState = typingText(shown),
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn()) togetherWith
                                (slideOutVertically { -it } + fadeOut()) using
                                SizeTransform(clip = true)
                        },
                        label = "typing names",
                    ) { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overlapping avatars. Those there from the start come in with the row; anyone who starts typing
 * later pops in with a bounce.
 */
@Composable
private fun Faces(
    typing: List<Typist>,
    resolver: MediaResolver,
) {
    val ring = MaterialTheme.colorScheme.surface
    val first = remember { typing.mapTo(HashSet()) { it.userId } }
    Row(
        Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        horizontalArrangement = Arrangement.spacedBy(-FACE_OVERLAP),
    ) {
        typing.forEach { typist ->
            key(typist.userId) {
                val scale = remember { Animatable(if (typist.userId in first) 1f else 0f) }
                LaunchedEffect(Unit) {
                    scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                }
                RoomAvatar(
                    typist.name,
                    typist.userId,
                    resolver.avatar(typist.avatarMxc),
                    size = FACE,
                    modifier =
                        Modifier
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }.border(2.dp, ring, CircleShape)
                            .clip(CircleShape),
                )
            }
        }
    }
}

/** Three dots rising and brightening one after another, round and round. */
@Composable
private fun Dots() {
    val context = LocalContext.current
    val still =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    val progress =
        if (still) {
            null
        } else {
            rememberInfiniteTransition(label = "typing").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(WAVE_MS, easing = LinearEasing)),
                label = "wave",
            )
        }
    val lift = with(LocalDensity.current) { DOT_LIFT.toPx() }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(DOTS) { i ->
            Box(
                Modifier
                    .size(DOT)
                    .graphicsLayer {
                        // Each dot's turn comes a little after the one before; a sine bump while it lasts.
                        val p = progress?.value ?: return@graphicsLayer
                        val t = ((p - i * DOT_DELAY) % 1f + 1f) % 1f
                        val bump = if (t < BUMP) sin(t / BUMP * PI).toFloat() else 0f
                        translationY = -bump * lift
                        alpha = DIM + (1f - DIM) * bump
                    }.background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun typingText(typing: List<Typist>): String =
    when (typing.size) {
        0 -> ""
        1 -> stringResource(R.string.typing_one, typing[0].name)
        2 -> stringResource(R.string.typing_two, typing[0].name, typing[1].name)
        else -> stringResource(R.string.typing_many)
    }

private val EDGE = 12.dp
private val FACE = 24.dp
private val FACE_OVERLAP = 8.dp
private val DOT = 6.dp
private val DOT_LIFT = 3.dp
private const val MAX_FACES = 3
private const val DOTS = 3
private const val WAVE_MS = 1_200
private const val DOT_DELAY = 0.15f

/** The part of the cycle a dot spends rising and falling. */
private const val BUMP = 0.45f

/** A resting dot's opacity. */
private const val DIM = 0.4f
