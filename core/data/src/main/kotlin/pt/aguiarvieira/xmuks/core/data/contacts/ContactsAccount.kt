package pt.aguiarvieira.xmuks.core.data.contacts

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder

/**
 * The account xmuks' contacts live under. Android wants an authenticator for any account type; this
 * one does nothing (xmuks signs in its own way) and offers no way to add an account from Settings.
 */
class ContactsAuthenticatorService : Service() {
    private val authenticator by lazy { Authenticator(this) }

    override fun onBind(intent: Intent?): IBinder? = authenticator.iBinder

    private class Authenticator(
        context: Context,
    ) : AbstractAccountAuthenticator(context) {
        override fun editProperties(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
        ): Bundle? = null

        override fun addAccount(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
            authTokenType: String?,
            requiredFeatures: Array<out String>?,
            options: Bundle?,
        ): Bundle =
            Bundle().apply { putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION) }

        override fun confirmCredentials(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            options: Bundle?,
        ): Bundle? = null

        override fun getAuthToken(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?,
        ): Bundle? = null

        override fun getAuthTokenLabel(authTokenType: String?): String? = null

        override fun updateCredentials(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?,
        ): Bundle? = null

        override fun hasFeatures(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            features: Array<out String>?,
        ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
    }
}

/**
 * Contacts declares an account's custom rows (our Message / Voice call / Video call, with their
 * icon and labels) through its sync adapter's CONTACTS_STRUCTURE. The syncing itself is xmuks'
 * (ContactsSync), so this adapter does nothing.
 */
class ContactsSyncAdapterService : Service() {
    private val adapter by lazy { Adapter(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = adapter.syncAdapterBinder

    private class Adapter(
        context: Context,
    ) : AbstractThreadedSyncAdapter(context, true) {
        override fun onPerformSync(
            account: Account?,
            extras: Bundle?,
            authority: String?,
            provider: ContentProviderClient?,
            syncResult: SyncResult?,
        ) = Unit
    }
}
