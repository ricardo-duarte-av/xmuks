package pt.aguiarvieira.xmuks.core.data.prefs

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Where a preference can be set, most specific first — the order a value is looked up in
 * (gomuks' `PreferenceContext`; its "config" level is unimplemented upstream, so absent here).
 */
enum class PrefScope(
    val isRoom: Boolean,
    val isDevice: Boolean,
) {
    /** This room, on this device only. */
    RoomDevice(isRoom = true, isDevice = true),

    /** This room, on every device (room account data). */
    RoomAccount(isRoom = true, isDevice = false),

    /** Every room, on this device only. */
    Device(isRoom = false, isDevice = true),

    /** Every room, on every device (account data `fi.mau.gomuks.preferences`). */
    Account(isRoom = false, isDevice = false),
}

/** One of gomuks' preferences: its key, the value when unset, and the scopes it may be set in. */
sealed class Pref<T>(
    val key: String,
    val default: T,
    val scopes: List<PrefScope>,
) {
    /** The stored value, when it's one this preference can hold. */
    abstract fun decode(json: JsonElement): T?

    abstract fun encode(value: T): JsonElement

    class Bool(
        key: String,
        default: Boolean,
        scopes: List<PrefScope>,
    ) : Pref<Boolean>(key, default, scopes) {
        override fun decode(json: JsonElement) = (json as? JsonPrimitive)?.booleanOrNull

        override fun encode(value: Boolean) = JsonPrimitive(value)
    }

    class Number(
        key: String,
        default: Int,
        scopes: List<PrefScope>,
    ) : Pref<Int>(key, default, scopes) {
        override fun decode(json: JsonElement) = (json as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

        override fun encode(value: Int) = JsonPrimitive(value)
    }

    /** One of [values]; null (JSON null) is a value in its own right where [values] has it. */
    class Choice(
        key: String,
        default: String?,
        scopes: List<PrefScope>,
        val values: List<String?>,
    ) : Pref<String?>(key, default, scopes) {
        override fun decode(json: JsonElement): String? =
            (json as? JsonPrimitive)?.contentOrNull?.takeIf { it in values }

        override fun encode(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
    }
}
