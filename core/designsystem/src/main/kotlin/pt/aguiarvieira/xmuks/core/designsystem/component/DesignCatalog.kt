package pt.aguiarvieira.xmuks.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A one-screen sample of the design system: colour roles, type (regular and rounded emphasized),
 * avatar silhouettes and expressive components. It is the subject of the screenshot tests, so a
 * theme regression shows up as a pixel diff.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesignCatalog(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, color = colors.surface) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("xmuks", style = MaterialTheme.typography.displaySmallEmphasized, color = colors.primary)
            Text("Headline · Google Sans Flex", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Body text for timeline messages, previews and settings. The quick brown fox jumps over the lazy dog.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch(colors.primary, colors.onPrimary, "P")
                Swatch(colors.primaryContainer, colors.onPrimaryContainer, "PC")
                Swatch(colors.secondaryContainer, colors.onSecondaryContainer, "SC")
                Swatch(colors.tertiaryContainer, colors.onTertiaryContainer, "TC")
                Swatch(colors.surfaceContainerHigh, colors.onSurface, "SH")
                Swatch(colors.error, colors.onError, "E")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialsAvatar(name = "Gomuks", id = "!gomuks:example.org", kind = AvatarKind.Room)
                InitialsAvatar(name = "Matrix HQ", id = "!hq:example.org", kind = AvatarKind.Room)
                InitialsAvatar(name = "@tulir", id = "@tulir:example.org", kind = AvatarKind.Person)
                InitialsAvatar(name = "Ana Ribeiro", id = "@ana:example.org", kind = AvatarKind.Person)
                InitialsAvatar(name = "Matrix", id = "!space:example.org", kind = AvatarKind.Space)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Send") }
                FilledTonalButton(onClick = {}) { Text("Reply") }
                OutlinedButton(onClick = {}) { Text("Cancel") }
            }
            Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }
    }
}

@Composable
private fun Swatch(
    container: Color,
    content: Color,
    label: String,
) {
    Box(
        modifier = Modifier.size(44.dp).background(container, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, style = MaterialTheme.typography.labelMedium)
    }
}
