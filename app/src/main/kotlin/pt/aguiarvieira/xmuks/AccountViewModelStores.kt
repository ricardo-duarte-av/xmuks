package pt.aguiarvieira.xmuks

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Each account's screens keep their view models apart. They're built from the account shown at
 * the time, so once another account is shown, the screens it replaced drop theirs and the new
 * account's start afresh. A configuration change keeps them, as an activity's would be kept.
 */
class AccountViewModelStores : ViewModel() {
    private val stores = HashMap<String, ViewModelStore>()

    fun store(account: String): ViewModelStore = stores.getOrPut(account, ::ViewModelStore)

    fun release(account: String) {
        stores.remove(account)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}

/** View models made under [content] are [account]'s, kept in [stores] until it stops being shown. */
@Composable
fun AccountViewModels(
    account: String,
    stores: AccountViewModelStores,
    activity: ComponentActivity,
    content: @Composable () -> Unit,
) {
    val owner = remember(account) { AccountViewModelOwner(stores.store(account), activity) }
    DisposableEffect(account) {
        onDispose { if (!activity.isChangingConfigurations) stores.release(account) }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** Made the activity's way (Hilt, saved state), but kept in the account's own store. */
private class AccountViewModelOwner(
    override val viewModelStore: ViewModelStore,
    private val activity: ComponentActivity,
) : ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = activity.defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() =
            MutableCreationExtras(activity.defaultViewModelCreationExtras).apply {
                set(VIEW_MODEL_STORE_OWNER_KEY, this@AccountViewModelOwner)
            }
}
