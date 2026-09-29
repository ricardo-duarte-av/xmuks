package pt.aguiarvieira.xmuks.feature.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.xmuks.core.data.timeline.MessageVersion
import pt.aguiarvieira.xmuks.core.richtext.HtmlContent
import pt.aguiarvieira.xmuks.core.richtext.PlainContent
import java.text.DateFormat
import java.util.Date

/** A message's history being looked at: its versions (edits), or what a deleted one said. */
data class HistoryView(
    val deleted: Boolean,
    /** Null while loading; empty when gomuks couldn't get it. */
    val versions: List<MessageVersion>?,
)

/** Every version of an edited message, oldest first — or a deleted message's content. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageHistorySheet(
    view: HistoryView,
    resolver: MediaResolver,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(if (view.deleted) R.string.deleted_title else R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            val versions = view.versions
            when {
                versions == null -> {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { LoadingIndicator() }
                }

                versions.isEmpty() -> {
                    Text(
                        stringResource(
                            if (view.deleted) R.string.deleted_unavailable else R.string.history_unavailable
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    LazyColumn(Modifier.heightIn(max = MAX_HEIGHT), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(versions) { index, version ->
                            if (index > 0) HorizontalDivider(Modifier.padding(bottom = 12.dp))
                            Version(version, view.deleted, resolver)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Version(
    version: MessageVersion,
    deleted: Boolean,
    resolver: MediaResolver,
) {
    val time =
        remember(version.timestamp) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(version.timestamp))
        }
    val label =
        when {
            deleted -> time
            version.edit -> stringResource(R.string.history_edit) + " · " + time
            else -> stringResource(R.string.history_original) + " · " + time
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        val color = MaterialTheme.colorScheme.onSurface
        val style = MaterialTheme.typography.bodyLarge
        val html = version.html
        if (html != null) {
            HtmlContent(html, color, style, { resolver.media(it, false) })
        } else {
            PlainContent(version.body.orEmpty(), color, style)
        }
    }
}

private val MAX_HEIGHT = 480.dp
