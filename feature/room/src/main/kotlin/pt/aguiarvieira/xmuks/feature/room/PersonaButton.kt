package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/**
 * Who the message will go out as: the per-message profile gomuks will pick for what's typed
 * (a trigger, or the default), or ourselves. Tapping picks the default. Absent without profiles.
 */
@Composable
internal fun PersonaButton(
    personas: Personas,
    draft: TextFieldState,
    avatarUrl: (String?) -> String?,
    onChoose: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (personas.choices.isEmpty()) return
    val active by remember(personas) { derivedStateOf { personas.active(draft.text.toString()) } }
    var open by remember { mutableStateOf(false) }
    val me = personas.me
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        val persona = active
        val description = stringResource(R.string.send_as, persona?.displayName ?: me?.displayName.orEmpty())
        val avatarModifier = Modifier.clip(CircleShape).clickable(onClickLabel = description) { open = true }
        if (persona != null) {
            RoomAvatar(
                persona.displayName ?: persona.id,
                persona.id,
                avatarUrl(persona.avatarMxc),
                avatarModifier,
                30.dp
            )
        } else {
            RoomAvatar(me?.displayName.orEmpty(), me?.userId.orEmpty(), me?.avatarUrl, avatarModifier, 30.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val chosen = personas.defaultId
            PersonaItem(stringResource(R.string.send_as_me), selected = chosen == null) {
                open = false
                onChoose(null)
            }
            personas.choices.forEach { p ->
                PersonaItem(p.displayName ?: p.id, selected = chosen == p.id) {
                    open = false
                    onChoose(p.id)
                }
            }
        }
    }
}

@Composable
private fun PersonaItem(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                name,
                fontWeight = if (selected) FontWeight.Bold else null,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        onClick = onClick,
    )
}
