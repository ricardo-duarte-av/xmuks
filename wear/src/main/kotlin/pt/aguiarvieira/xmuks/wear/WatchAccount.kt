package pt.aguiarvieira.xmuks.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.wear.phone.interactions.notifications.BridgingConfig
import androidx.wear.phone.interactions.notifications.BridgingManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import pt.aguiarvieira.xmuks.core.account.CredentialStore
import pt.aguiarvieira.xmuks.core.account.LoginHandoff
import pt.aguiarvieira.xmuks.core.account.PushRegistrar
import pt.aguiarvieira.xmuks.core.account.WatchLink
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthResult
import pt.aguiarvieira.xmuks.core.network.Credentials
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The watch's own gomuks login, handed over by the phone app. Signed in, the watch gets its own
 * pushes and the phone's notifications stop being bridged here (they'd show twice); signed out,
 * bridging is back, so the watch still shows the phone's notifications.
 */
@Singleton
class WatchAccount
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: CredentialStore,
        private val authApi: AuthApi,
        private val registrar: PushRegistrar,
        @Named("watch") private val settings: DataStore<Preferences>,
    ) {
        enum class SignIn { Ok, NoPhone, PhoneSignedOut, LoginFailed, NoNetwork }

        /** Who we're signed in as (the gomuks username); null when signed out. */
        fun signedInAs(): String? = store.credentials()?.username

        val sendReadReceipts: Flow<Boolean> = settings.data.map { it[SEND_READ_RECEIPTS] ?: true }

        suspend fun signInFromPhone(): SignIn {
            val answer = withTimeoutOrNull(PHONE_TIMEOUT_MS) { askPhone() } ?: return SignIn.NoPhone
            // An empty answer: the phone app isn't signed in.
            val handoff = WatchLink.decode(answer) ?: return SignIn.PhoneSignedOut
            return logIn(handoff)
        }

        /** The phone app's answer to our login request; null when no phone app could be reached. */
        private suspend fun askPhone(): ByteArray? =
            runCatching {
                val nodes =
                    Wearable
                        .getCapabilityClient(context)
                        .getCapability(WatchLink.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                        .await()
                        .nodes
                val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return null
                Wearable
                    .getMessageClient(context)
                    .sendRequest(phone.id, WatchLink.LOGIN_PATH, ByteArray(0))
                    .await()
            }.getOrNull()

        /** Logs in with [handoff] for ourselves: our own gomuks session and our own pusher. */
        suspend fun logIn(handoff: LoginHandoff): SignIn {
            val server = handoff.serverUrl.toHttpUrlOrNull() ?: return SignIn.PhoneSignedOut
            val credentials = Credentials(server, handoff.username, handoff.password)
            return when (val result = withContext(Dispatchers.IO) { authApi.login(credentials) }) {
                is AuthResult.Success -> {
                    store.saveLogin(credentials, result.token)
                    settings.edit { it[SEND_READ_RECEIPTS] = handoff.sendReadReceipts }
                    registrar.ensureRegistered(force = true)
                    applyBridging()
                    SignIn.Ok
                }

                AuthResult.BadCredentials, AuthResult.InsecureTransport -> {
                    SignIn.LoginFailed
                }

                is AuthResult.ServerError, is AuthResult.NetworkError -> {
                    SignIn.NoNetwork
                }
            }
        }

        /** Stops gomuks pushing here (best effort), then forgets the login. */
        suspend fun signOut() {
            registrar.beforeLogout()
            registrar.clearAccountData()
            store.clear()
            applyBridging()
        }

        /** The phone's notifications are bridged here only while the watch has none of its own. */
        fun applyBridging() {
            runCatching {
                BridgingManager
                    .fromContext(context)
                    .setConfig(BridgingConfig.Builder(context, signedInAs() == null).build())
            }
        }

        suspend fun receiptType(): String = if (sendReadReceipts.first()) "m.read" else "m.read.private"

        private companion object {
            val SEND_READ_RECEIPTS = booleanPreferencesKey("send_read_receipts")
            const val PHONE_TIMEOUT_MS = 15_000L
        }
    }
