package pt.aguiarvieira.xmuks.core.data.timeline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.account.AccountScoped

/**
 * The pinned bar each room's timeline had closed: the pins it showed then. It stays closed until
 * something else is pinned. This device's only, across restarts.
 */
class PinBarStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : AccountScoped {
    /** The pins [roomId]'s bar was closed on (empty: it never was). */
    fun closedOn(roomId: String): Flow<Set<String>> =
        dataStore.data.map { it[stringSetPreferencesKey(roomId)].orEmpty() }.distinctUntilChanged()

    /** Closes [roomId]'s bar while [pins] are all it would show. */
    fun close(
        roomId: String,
        pins: Collection<String>,
    ) {
        scope.launch { dataStore.edit { it[stringSetPreferencesKey(roomId)] = pins.toSet() } }
    }

    override suspend fun clearAccountData() {
        dataStore.edit { it.clear() }
    }
}
