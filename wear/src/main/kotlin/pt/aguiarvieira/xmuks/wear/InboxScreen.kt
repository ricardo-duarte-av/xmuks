package pt.aguiarvieira.xmuks.wear

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import pt.aguiarvieira.xmuks.core.notify.Notified
import pt.aguiarvieira.xmuks.core.notify.author
import java.text.DateFormat

/**
 * The notifications gomuks recorded, newest first: everything that notified, or only mentions.
 * Loaded when opened, a page at a time; a message opens on its own screen.
 */
@Composable
fun InboxScreen(
    inbox: Inbox,
    images: ImageLoader,
    server: HttpUrl?,
    onOpen: (Notified) -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(Inbox.Kind.All) }
    val messages = remember { mutableStateListOf<Notified>() }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }

    suspend fun load(fresh: Boolean) {
        loading = true
        failed = false
        // Older than the oldest we have: gomuks' bound is inclusive.
        val oldest = messages.lastOrNull()?.message?.timestamp
        val before = if (fresh || oldest == null) System.currentTimeMillis() else oldest - 1
        inbox
            .page(kind, before)
            .onSuccess { page ->
                if (fresh) messages.clear()
                messages.addAll(page.filter { m -> messages.none { it.message.eventId == m.message.eventId } })
                more = page.isNotEmpty()
            }.onFailure { failed = true }
        loading = false
    }

    LaunchedEffect(kind) { load(fresh = true) }

    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state, modifier = modifier) { padding ->
        TransformingLazyColumn(
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                val title = if (kind == Inbox.Kind.All) R.string.inbox_all else R.string.inbox_mentions
                ListHeader { Text(stringResource(title)) }
            }
            item {
                Button(
                    onClick = { kind = if (kind == Inbox.Kind.All) Inbox.Kind.Mentions else Inbox.Kind.All },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (kind == Inbox.Kind.All) R.string.show_mentions else R.string.show_all))
                }
            }
            items(messages, key = { it.message.eventId }) { notified ->
                MessageCard(notified, images, server, onClick = { onOpen(notified) })
            }
            item {
                when {
                    loading -> {
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }

                    failed -> {
                        Button(
                            onClick = { scope.launch { load(fresh = messages.isEmpty()) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.retry)) }
                    }

                    messages.isEmpty() -> {
                        Text(
                            stringResource(R.string.inbox_empty),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    more -> {
                        Button(onClick = { scope.launch { load(fresh = false) } }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.load_more))
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onAccount,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.account)) }
            }
        }
    }
}

@Composable
private fun MessageCard(
    notified: Notified,
    images: ImageLoader,
    server: HttpUrl?,
    onClick: () -> Unit,
) {
    val message = notified.message
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(message.roomAvatar, images, server, AVATAR)
            Column {
                Text(
                    message.roomName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // In a DM the room is the sender: their name is already there.
                if (!message.isDm) {
                    Text(
                        message.author.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            notified.text.toString(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(sentAt(message.timestamp), style = MaterialTheme.typography.labelSmall)
    }
}

/** A gomuks media path's picture, round; a plain circle while it loads, or without one. */
@Composable
fun Avatar(
    path: String?,
    images: ImageLoader,
    server: HttpUrl?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val url = path?.let { server?.resolve(it) }?.toString()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                imageLoader = images,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Today's time alone, an earlier day's date. */
fun sentAt(timestamp: Long): String =
    DateUtils.formatSameDayTime(timestamp, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()

private val AVATAR = 28.dp
