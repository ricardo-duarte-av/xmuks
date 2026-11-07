package pt.aguiarvieira.xmuks.feature.roomlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.designsystem.component.RoomAvatar

/**
 * The top bar's edit control: a pencil that turns, in place, into the save button (tonal, with
 * the number of pending changes on it, and a loading indicator while the changes go out).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpaceEditAction(
    edit: SpaceEditSession?,
    onEdit: () -> Unit,
    onSave: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = edit?.let { if (it.saving) EditButton.Saving else EditButton.Save } ?: EditButton.Edit,
        transitionSpec = {
            (scaleIn(motion.fastSpatialSpec()) togetherWith scaleOut(motion.fastSpatialSpec()))
        },
        contentAlignment = Alignment.Center,
        label = "space edit action",
    ) { button ->
        when (button) {
            EditButton.Edit -> {
                IconButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.space_edit))
                }
            }

            EditButton.Save -> {
                val changes = edit?.changes ?: 0
                BadgedBox(
                    badge = { if (changes > 0) Badge { Text(changes.toString()) } },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = onSave,
                        enabled = changes > 0,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.space_edit_save)
                        )
                    }
                }
            }

            EditButton.Saving -> {
                Box(Modifier.size(48.dp).padding(end = 8.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(40.dp))
                }
            }
        }
    }
}

private enum class EditButton { Edit, Save, Saving }

/** The editor's list: a filter on top, rooms already in the space, then everything else. */
@Composable
internal fun SpaceEditList(
    rows: List<SpaceEditRow>?,
    selected: Set<String>,
    query: TextFieldState,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxSize()) {
        SearchField(query, stringResource(R.string.space_edit_filter), padding = PaddingValues(12.dp))
        when {
            rows == null -> {
                Box(Modifier.fillMaxSize())
            }

            rows.isEmpty() -> {
                EmptyState(stringResource(R.string.space_edit_none))
            }

            else -> {
                val (inside, outside) = rows.partition { it.wasIn }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    section(R.string.space_edit_in_space, inside, selected, enabled, onToggle)
                    section(R.string.space_edit_other, outside, selected, enabled, onToggle)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int,
    rows: List<SpaceEditRow>,
    selected: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "heading:$title", contentType = "heading") {
        Text(
            "${stringResource(title)} · ${rows.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.animateItem().padding(start = 12.dp, top = 12.dp, bottom = 6.dp),
        )
    }
    items(rows, key = { it.room.roomId }, contentType = { "row" }) { row ->
        SpaceEditItem(
            row,
            checked = row.room.roomId in selected,
            enabled = enabled,
            onToggle = { onToggle(row.room.roomId) },
            modifier = Modifier.animateItem(),
        )
    }
}

/**
 * One room: a checkbox, its avatar, its name and what it is. A ticked row fills with the
 * secondary container and rounds off; the change springs, as expressive selection does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpaceEditItem(
    row: SpaceEditRow,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(if (checked) 28.dp else 8.dp, motion.defaultSpatialSpec(), label = "corner")
    val container by animateColorAsState(
        if (checked) colors.secondaryContainer else colors.surfaceContainerLow,
        motion.defaultEffectsSpec(),
        label = "container",
    )
    val room = row.room
    val kind =
        when {
            room.parents.isNotEmpty() -> stringResource(R.string.space_edit_also_in, room.parents.joinToString())
            room.isSpace -> stringResource(R.string.space_edit_kind_space)
            room.isDirect -> stringResource(R.string.space_edit_kind_dm)
            else -> null
        }
    Surface(
        shape = RoundedCornerShape(corner),
        color = container,
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
            RoomAvatar(name = room.name, id = room.roomId, avatarUrl = room.avatarUrl, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (checked) colors.onSecondaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                kind?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
