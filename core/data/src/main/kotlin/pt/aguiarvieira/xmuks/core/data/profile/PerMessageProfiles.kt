package pt.aguiarvieira.xmuks.core.data.profile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import pt.aguiarvieira.xmuks.core.data.timeline.str

/**
 * Per-message profiles (MSC4461): named personas we can send as. gomuks applies them itself when it
 * sends — by trigger (`prefix`…`suffix` around the text) or, failing that, the default — so all a
 * client does is edit the account data. Fields we don't know are kept as they were.
 */
data class PerMessageProfiles(
    val defaultId: String?,
    val profiles: List<PerMessageProfile>,
    private val raw: JsonObject = JsonObject(emptyMap()),
) {
    val default: PerMessageProfile? get() = profiles.firstOrNull { it.id == defaultId }

    fun toJson(): JsonObject =
        JsonObject(
            raw - DEFAULT_ID - PROFILES +
                buildMap {
                    defaultId?.let { put(DEFAULT_ID, JsonPrimitive(it)) }
                    put(PROFILES, JsonArray(profiles.map(PerMessageProfile::toJson)))
                },
        )

    /** [profile] replacing the one with its id, or added at the end. */
    fun upsert(profile: PerMessageProfile): PerMessageProfiles {
        val index = profiles.indexOfFirst { it.id == profile.id }
        val list = if (index < 0) profiles + profile else profiles.toMutableList().also { it[index] = profile }
        return copy(profiles = list)
    }

    fun remove(id: String): PerMessageProfiles =
        copy(profiles = profiles.filter { it.id != id }, defaultId = defaultId.takeUnless { it == id })

    companion object {
        /** What gomuks reads (mautrix `AccountDataPerMessageProfiles`). */
        const val TYPE = "fi.mau.msc4461.per_message_profiles.v3"
        private const val DEFAULT_ID = "default_profile_id"
        private const val PROFILES = "profiles"

        val EMPTY = PerMessageProfiles(null, emptyList())

        fun parse(content: JsonObject?): PerMessageProfiles {
            content ?: return EMPTY
            val profiles =
                (content[PROFILES] as? JsonArray).orEmpty().mapNotNull {
                    (it as? JsonObject)?.let(
                        PerMessageProfile::parse
                    )
                }
            return PerMessageProfiles(content.str(DEFAULT_ID)?.takeIf { it.isNotEmpty() }, profiles, content)
        }
    }
}

data class PerMessageProfile(
    val id: String,
    val displayName: String?,
    val avatarMxc: String?,
    val triggers: List<Trigger>,
    private val raw: JsonObject = JsonObject(emptyMap()),
) {
    /** Sends as this profile when the text starts with [prefix] and ends with [suffix]. */
    data class Trigger(
        val prefix: String,
        val suffix: String,
        /** Leave the trigger in the sent text instead of stripping it. */
        val keepTrigger: Boolean = false,
    ) {
        /** How it reads in a list: `prefix…suffix`. */
        val label: String get() = prefix + "…" + suffix
    }

    fun toJson(): JsonObject =
        JsonObject(
            raw - ID - NAME - AVATAR - TRIGGERS +
                buildMap<String, JsonElement> {
                    put(ID, JsonPrimitive(id))
                    displayName?.takeIf { it.isNotBlank() }?.let { put(NAME, JsonPrimitive(it)) }
                    avatarMxc?.takeIf { it.isNotBlank() }?.let { put(AVATAR, JsonPrimitive(it)) }
                    if (triggers.isNotEmpty()) put(TRIGGERS, JsonArray(triggers.map { it.toJson() }))
                },
        )

    private fun Trigger.toJson(): JsonObject =
        JsonObject(
            buildMap {
                if (prefix.isNotEmpty()) put("prefix", JsonPrimitive(prefix))
                if (suffix.isNotEmpty()) put("suffix", JsonPrimitive(suffix))
                if (keepTrigger) put("keep_trigger", JsonPrimitive(true))
            },
        )

    companion object {
        private const val ID = "id"
        private const val NAME = "displayname"
        private const val AVATAR = "avatar_url"
        private const val TRIGGERS = "triggers"

        fun parse(obj: JsonObject): PerMessageProfile? {
            val id = obj.str(ID)?.takeIf { it.isNotEmpty() } ?: return null
            val triggers =
                (obj[TRIGGERS] as? JsonArray).orEmpty().mapNotNull { t ->
                    val trigger = t as? JsonObject ?: return@mapNotNull null
                    val keep = (trigger["keep_trigger"] as? JsonPrimitive)?.booleanOrNull == true
                    Trigger(trigger.str("prefix").orEmpty(), trigger.str("suffix").orEmpty(), keep)
                        .takeIf { it.prefix.isNotEmpty() || it.suffix.isNotEmpty() }
                }
            return PerMessageProfile(id, obj.str(NAME), obj.str(AVATAR), triggers, obj)
        }
    }
}
