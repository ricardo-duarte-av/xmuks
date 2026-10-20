package pt.aguiarvieira.xmuks.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.notify.Notified
import pt.aguiarvieira.xmuks.core.notify.author
import javax.inject.Inject

/** One message from the inbox, whole: who, where, all its text, its picture; and marking it read. */
@AndroidEntryPoint
class MessageActivity : ComponentActivity() {
    @Inject lateinit var inbox: Inbox

    @Inject lateinit var actions: WatchActions

    @Inject lateinit var images: ImageLoader

    @Inject lateinit var store: CredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only what the inbox loaded can be shown: after the process was gone, back to it.
        val notified = intent.getStringExtra(EVENT_ID)?.let(inbox::find) ?: return finish()
        setContent {
            MaterialTheme {
                AppScaffold {
                    MessageScreen(notified, images, store.credentials()?.serverUrl, actions, onDone = ::finish)
                }
            }
        }
    }

    companion object {
        private const val EVENT_ID = "event_id"

        fun intent(
            context: Context,
            eventId: String,
        ): Intent = Intent(context, MessageActivity::class.java).putExtra(EVENT_ID, eventId)
    }
}

@Composable
private fun MessageScreen(
    notified: Notified,
    images: ImageLoader,
    server: HttpUrl?,
    actions: WatchActions,
    onDone: () -> Unit,
) {
    val message = notified.message
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(message.author.avatar ?: message.roomAvatar, images, server, AVATAR)
                    Text(message.author.name, style = MaterialTheme.typography.titleSmall)
                }
            }
            if (!message.isDm) {
                item { Text(message.roomName, style = MaterialTheme.typography.labelSmall) }
            }
            val picture = notified.picture?.let { server?.resolve(it) }?.toString()
            if (picture != null) {
                item {
                    AsyncImage(
                        model = picture,
                        contentDescription = notified.text.toString(),
                        imageLoader = images,
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = PICTURE_MAX)
                                .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            item { Text(notified.text.toString(), style = MaterialTheme.typography.bodyMedium) }
            notified.caption?.let { caption ->
                item { Text(caption.toString(), style = MaterialTheme.typography.bodyMedium) }
            }
            item { Text(sentAt(message.timestamp), style = MaterialTheme.typography.labelSmall) }
            item {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val ok = actions.markRead(message.roomId, message.eventId)
                            busy = false
                            failed = !ok
                            if (ok) onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (failed) R.string.mark_read_failed else R.string.mark_read))
                }
            }
        }
    }
}

private val AVATAR = 32.dp
private val PICTURE_MAX = 160.dp
