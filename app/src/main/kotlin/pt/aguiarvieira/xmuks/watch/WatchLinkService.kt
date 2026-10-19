package pt.aguiarvieira.xmuks.watch

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.account.LoginHandoff
import pt.aguiarvieira.xmuks.core.account.WatchLink
import pt.aguiarvieira.xmuks.core.data.prefs.PreferenceStore
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs
import javax.inject.Inject

/**
 * Answers the watch app's sign-in request with our gomuks login. Only our own watch app can ask:
 * the Data Layer connects apps with the same package name and signing key, nothing else.
 */
@AndroidEntryPoint
class WatchLinkService : WearableListenerService() {
    @Inject lateinit var store: CredentialStore

    @Inject lateinit var preferences: PreferenceStore

    override fun onRequest(
        nodeId: String,
        path: String,
        request: ByteArray,
    ): Task<ByteArray>? {
        if (path != WatchLink.LOGIN_PATH) return null
        val answer =
            runBlocking {
                val credentials = store.credentials() ?: return@runBlocking ByteArray(0)
                val receipts = runCatching { preferences.value(Prefs.sendReadReceipts).first() }.getOrDefault(true)
                WatchLink.encode(
                    LoginHandoff(
                        serverUrl = credentials.serverUrl.toString(),
                        username = credentials.username,
                        password = credentials.password,
                        sendReadReceipts = receipts,
                    ),
                )
            }
        return Tasks.forResult(answer)
    }
}
