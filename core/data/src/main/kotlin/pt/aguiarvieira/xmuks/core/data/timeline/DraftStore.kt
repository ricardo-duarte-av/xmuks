package pt.aguiarvieira.xmuks.core.data.timeline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.aguiarvieira.xmuks.core.data.connection.AccountScoped

/**
 * What was being written in each room, kept when the room is left and across restarts. Unsent
 * text only: a message on its way lives in the outbox, not here.
 */
class DraftStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : AccountScoped {
    /** One writer, so saves land in the order they were made. */
    private val writes = Channel<Pair<String, String>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for ((roomId, text) in writes) {
                dataStore.edit { prefs ->
                    val key = stringPreferencesKey(roomId)
                    if (text.isBlank()) prefs.remove(key) else prefs[key] = text
                }
            }
        }
    }

    suspend fun load(roomId: String): String? = dataStore.data.first()[stringPreferencesKey(roomId)]

    /** Saves in the background (it outlives the screen that asked); blank text removes the draft. */
    fun save(
        roomId: String,
        text: String,
    ) {
        writes.trySend(roomId to text)
    }

    override suspend fun clearAccountData() {
        dataStore.edit { it.clear() }
    }
}
