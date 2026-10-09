package pt.aguiarvieira.xmuks.feature.call

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit

/**
 * From Android 14 only calling apps may ring over the lock screen unless the user allows it. Asked
 * once, in a call (when it makes sense): otherwise calls still ring, but as a heads-up.
 */
@Composable
internal fun FullScreenRingPrompt() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(needsAsking(context)) }
    if (!show) return

    fun done() {
        show = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(ASKED, true) }
    }
    AlertDialog(
        onDismissRequest = ::done,
        title = { Text(stringResource(R.string.call_full_screen_title)) },
        text = { Text(stringResource(R.string.call_full_screen_text)) },
        confirmButton = {
            TextButton(onClick = {
                done()
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        ),
                    )
                }
            }) { Text(stringResource(R.string.call_full_screen_allow)) }
        },
        dismissButton = { TextButton(onClick = ::done) { Text(stringResource(R.string.call_full_screen_later)) } },
    )
}

private fun needsAsking(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    if (context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) return false
    return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED, false)
}

private const val PREFS = "calls"
private const val ASKED = "asked_full_screen"
