package pt.aguiarvieira.xmuks.feature.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.call.CallReaction
import pt.aguiarvieira.xmuks.core.call.CallReactions

/** Raise a hand, or send one of Element Call's reactions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReactionSheet(
    handRaised: Boolean,
    onHand: (Boolean) -> Unit,
    onReact: (CallReaction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val hand = stringResource(if (handRaised) R.string.call_lower_hand else R.string.call_raise_hand)
            if (handRaised) {
                FilledTonalButton(onClick = {
                    onHand(false)
                }, modifier = Modifier.fillMaxWidth()) { Text("${CallReactions.HAND}  $hand") }
            } else {
                Button(
                    onClick = { onHand(true) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("${CallReactions.HAND}  $hand") }
            }
            FlowRow(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                CallReactions.SET.forEach { reaction ->
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip56()
                            .clickable { onReact(reaction) },
                        contentAlignment = Alignment.Center,
                    ) { Text(reaction.emoji, fontSize = 30.sp) }
                }
            }
        }
    }
}

private fun Modifier.clip56() = this.then(Modifier.background(Color.Transparent, CircleShape))

/** The time call screens count from; tests pin it. */
internal val LocalCallClock = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/** "✋ 0:42" in a tile's corner while that person's hand is up. */
@Composable
internal fun HandBadge(
    raisedAt: Long,
    modifier: Modifier = Modifier,
) {
    val clock = LocalCallClock.current
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(raisedAt) {
        while (true) {
            now = clock()
            delay(TICK_MS)
        }
    }
    Row(
        modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(CallReactions.HAND, fontSize = 16.sp)
        Text(
            formatDuration(now - raisedAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** A reaction rising from the bottom of a tile and fading away. */
@Composable
internal fun FloatingReaction(
    emoji: String,
    modifier: Modifier = Modifier,
) {
    val rise = remember(emoji) { Animatable(0f) }
    LaunchedEffect(emoji) {
        launch { rise.animateTo(1f, tween(RISE_MS)) }
    }
    Text(
        emoji,
        fontSize = 44.sp,
        modifier = modifier.offset(y = (-RISE_DP * rise.value).dp).alpha(1f - rise.value * rise.value),
    )
}

private const val TICK_MS = 1_000L
private const val RISE_MS = 3_000
private const val RISE_DP = 120f
