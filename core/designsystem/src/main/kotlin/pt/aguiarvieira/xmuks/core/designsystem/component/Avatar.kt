package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toColor

/**
 * Placeholder avatar: initials on a tone derived from [id], so the same room or user gets the same
 * colour on every screen, in notifications and across launches. Real images come in with Coil
 * (M6); this is what shows while they load or when there is none.
 */
@Composable
fun InitialsAvatar(
    name: String,
    id: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (container, content) = remember(id, dark) { avatarColors(id, dark) }
    Box(
        modifier =
            modifier
                .size(size)
                .background(container, AvatarShape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            color = content,
            style = MaterialTheme.typography.titleMediumEmphasized,
            fontSize = (size.value * 0.36f).sp,
        )
    }
}

/**
 * Up to two initials from a display name, skipping Matrix sigils (`@`, `#`, `!`, `+`) and
 * punctuation. Returns an empty string for names with no letters or digits.
 */
fun initialsOf(name: String): String =
    name
        .split(' ', '-', '_', '.', ':')
        .mapNotNull { word ->
            word
                .codePoints()
                .filter(Character::isLetterOrDigit)
                .findFirst()
                .orElse(-1)
        }.filter { it >= 0 }
        .take(2)
        .joinToString("") { String(Character.toChars(it)).uppercase() }

private const val HUE_BUCKETS = 12
private const val CONTAINER_CHROMA = 36.0

/** Container/content pair at a hue picked from [id]; tones follow M3's container roles. */
internal fun avatarColors(
    id: String,
    dark: Boolean,
): Pair<Color, Color> {
    val hue = Math.floorMod(id.hashCode(), HUE_BUCKETS) * (360.0 / HUE_BUCKETS)
    val containerTone = if (dark) 30.0 else 90.0
    val contentTone = if (dark) 90.0 else 10.0
    return Hct.from(hue, CONTAINER_CHROMA, containerTone).toColor() to
        Hct.from(hue, CONTAINER_CHROMA, contentTone).toColor()
}
