package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pt.aguiarvieira.xmuks.core.data.timeline.TimelineItem

/** Which sheet or dialog the room screen has open (at most one of each). */
internal class OverlayState {
    var unsent by mutableStateOf<TimelineItem.Message?>(null)
    var menuFor by mutableStateOf<TimelineItem.Message?>(null)
    var deleting by mutableStateOf<TimelineItem.Message?>(null)
    var picker by mutableStateOf<PickerRequest?>(null)
}

/** An open picker: for a reaction to [reactTo], or for the composer. */
internal class PickerRequest(
    val mode: PickerMode,
    val reactTo: TimelineItem.Message? = null,
)

/** The room's sheets and dialogs: message menu, emoji/sticker picker, delete, history, unsent. */
@Composable
internal fun RoomOverlays(
    state: OverlayState,
    composer: ComposerActions,
    resolver: MediaResolver,
) {
    state.menuFor?.let { message ->
        MessageMenu(
            message,
            onReply = {
                composer.onReply(message)
                state.menuFor = null
            },
            onEdit = {
                composer.onEdit(message)
                state.menuFor = null
            },
            onHistory = {
                composer.onShowHistory(message)
                state.menuFor = null
            },
            onDelete = {
                state.deleting = message
                state.menuFor = null
            },
            quickReactions = quickReactions(composer.emoji.recent),
            onReact = { key ->
                composer.emoji.onReact(message, Picked.Unicode(key))
                state.menuFor = null
            },
            onMoreReactions = {
                state.picker = PickerRequest(PickerMode.Emoji, reactTo = message)
                state.menuFor = null
            },
            onDismiss = { state.menuFor = null },
        )
    }
    state.picker?.let { request ->
        EmojiPickerSheet(
            mode = request.mode,
            packs = composer.emoji.packs,
            recent = composer.emoji.recent,
            resolver = resolver,
            onPick = { picked ->
                val target = request.reactTo
                when {
                    target != null -> {
                        composer.emoji.onReact(target, picked)
                    }

                    request.mode == PickerMode.Sticker -> {
                        (picked as? Picked.Custom)?.let { composer.emoji.onSticker(it.image) }
                    }

                    else -> {
                        composer.draft.insertAtCursor(picked.asText())
                        composer.emoji.onUsed(picked)
                    }
                }
                // Inserting into the composer keeps the state.picker open for more; the rest close it.
                if (target != null || request.mode == PickerMode.Sticker) state.picker = null
            },
            onSubscribe = composer.emoji.onSubscribe,
            onDismiss = { state.picker = null },
        )
    }
    state.deleting?.let { message ->
        DeleteDialog(
            onConfirm = {
                composer.onDelete(message)
                state.deleting = null
            },
            onDismiss = { state.deleting = null },
        )
    }
    composer.history?.let { MessageHistorySheet(it, resolver, composer.onHideHistory) }
    state.unsent?.let { message ->
        val id = message.localId ?: return@let
        UnsentDialog(
            message,
            onResend = {
                composer.onResend(id)
                state.unsent = null
            },
            onDiscard = {
                composer.onDiscard(id)
                state.unsent = null
            },
            onDismiss = { state.unsent = null },
        )
    }
}

/** The most used Unicode emoji (custom ones need the picker), topped up with the usual ones. */
private fun quickReactions(recent: List<String>): List<String> =
    (recent.filterNot { it.startsWith("mxc://") } + DEFAULT_QUICK_REACTIONS).distinct().take(QUICK_REACTIONS)

/** Emoji into the draft: Unicode as is, custom ones as `:shortcode:` (expanded when sent). */
private fun Picked.asText(): String =
    when (this) {
        is Picked.Unicode -> emoji
        is Picked.Custom -> ":${image.shortcode}:"
    }

private fun TextFieldState.insertAtCursor(text: String) {
    edit { replace(selection.min, selection.max, text) }
}

private const val QUICK_REACTIONS = 6
