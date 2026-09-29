package pt.aguiarvieira.xmuks.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pt.aguiarvieira.xmuks.core.data.connection.AccountScoped
import pt.aguiarvieira.xmuks.core.data.connection.toResult
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.ExecMode
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/** What each scope holds, for one room (or none): the values a preference is looked up in. */
data class PrefLayers(
    val values: Map<PrefScope, JsonObject>,
) {
    /** [pref]'s value: from the most specific scope it's allowed in and set in, else its default. */
    fun <T> get(pref: Pref<T>): T = pref.scopes.firstNotNullOfOrNull { lookup(pref, it) } ?: pref.default

    /** Where [pref]'s value comes from; null for its default. */
    fun source(pref: Pref<*>): PrefScope? = pref.scopes.firstOrNull { lookup(pref, it) != null }

    /** [pref]'s value set in [scope] itself, if any. */
    fun <T> lookup(
        pref: Pref<T>,
        scope: PrefScope,
    ): T? = values[scope]?.get(pref.key)?.let(pref::decode)

    companion object {
        val EMPTY = PrefLayers(emptyMap())
    }
}

/**
 * gomuks' preferences in their four scopes: the account's and each room's (account data
 * `fi.mau.gomuks.preferences`, shared with gomuks web), and this device's, globally and per room
 * (kept here, never synced).
 */
class PreferenceStore(
    private val exec: ExecClient,
    database: XmuksDatabase,
    private val device: DataStore<Preferences>,
) : AccountScoped {
    private val dao = database.roomListDao()

    /** Every scope's values for [roomId] (null: no room — only the global scopes). */
    fun layers(roomId: String? = null): Flow<PrefLayers> {
        val account = accountData("")
        val deviceGlobal = deviceValues(GLOBAL_KEY)
        val roomAccount = roomId?.let(::accountData) ?: flowOf(null)
        val roomDevice = roomId?.let { deviceValues(ROOM_PREFIX + it) } ?: flowOf(null)
        return combine(roomDevice, roomAccount, deviceGlobal, account) { rd, ra, d, a ->
            PrefLayers(
                buildMap {
                    rd?.let { put(PrefScope.RoomDevice, it) }
                    ra?.let { put(PrefScope.RoomAccount, it) }
                    d?.let { put(PrefScope.Device, it) }
                    a?.let { put(PrefScope.Account, it) }
                },
            )
        }.distinctUntilChanged()
    }

    /** One preference's value in [roomId] (or globally), live. */
    fun <T> value(
        pref: Pref<T>,
        roomId: String? = null,
    ): Flow<T> = layers(roomId).map { it.get(pref) }.distinctUntilChanged()

    /** Sets [pref] to [value] in [scope] (a room scope needs [roomId]). */
    suspend fun <T> set(
        pref: Pref<T>,
        scope: PrefScope,
        value: T,
        roomId: String? = null,
    ): Result<Unit> = write(pref, scope, pref.encode(value), roomId)

    /** Removes [pref] from [scope]: the next scope's value (or the default) applies again. */
    suspend fun clear(
        pref: Pref<*>,
        scope: PrefScope,
        roomId: String? = null,
    ): Result<Unit> = write(pref, scope, null, roomId)

    private suspend fun write(
        pref: Pref<*>,
        scope: PrefScope,
        encoded: JsonElement?,
        roomId: String?,
    ): Result<Unit> {
        require(scope in pref.scopes) { "${pref.key} can't be set in $scope" }
        require(!scope.isRoom || roomId != null) { "$scope needs a room" }
        if (scope.isDevice) {
            editDevice(if (scope.isRoom) ROOM_PREFIX + roomId else GLOBAL_KEY, pref.key, encoded)
            return Result.success(Unit)
        }
        val current = accountData(if (scope.isRoom) roomId.orEmpty() else "").first() ?: JsonObject(emptyMap())
        val updated = if (encoded == null) current - pref.key else current + (pref.key to encoded)
        val params =
            buildJsonObject {
                if (scope.isRoom) put("room_id", JsonPrimitive(roomId))
                put("type", JsonPrimitive(TYPE))
                put("content", JsonObject(updated))
            }
        return exec.exec("set_account_data", params, ExecMode.Write).toResult().map { }
    }

    private fun accountData(roomId: String): Flow<JsonObject?> = dao.accountData(roomId, TYPE).map(::parse)

    private fun deviceValues(name: String): Flow<JsonObject?> =
        device.data.map { it[stringPreferencesKey(name)]?.let(::parse) }

    private suspend fun editDevice(
        name: String,
        key: String,
        value: JsonElement?,
    ) {
        val stored = stringPreferencesKey(name)
        device.edit { prefs ->
            val current = prefs[stored]?.let(::parse) ?: JsonObject(emptyMap())
            val updated = if (value == null) current - key else current + (key to value)
            if (updated.isEmpty()) prefs.remove(stored) else prefs[stored] = JsonObject(updated).toString()
        }
    }

    private fun parse(json: String?): JsonObject? =
        json?.let { runCatching { GomuksJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    /** This device's choices belong to the account they were made under. */
    override suspend fun clearAccountData() {
        device.edit { it.clear() }
    }

    companion object {
        const val TYPE = "fi.mau.gomuks.preferences"
        private const val GLOBAL_KEY = "global"
        private const val ROOM_PREFIX = "room:"
    }
}
