package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.protocol.Event
import java.net.URI
import java.net.URLEncoder

/**
 * A room widget (`im.vector.modular.widgets`, or the spec's `m.widget`): a web page the room
 * offers. [template] still has its `$variables`; [url] fills them in.
 */
data class Widget(
    /** The state key: one widget per key. */
    val id: String,
    val name: String,
    val type: String,
    /** What it says it shows (`data.title`), if anything. */
    val title: String?,
    val creator: String?,
    val template: String,
    /** The widget's `data` strings, each a `$key` its URL may name. */
    val data: Map<String, String> = emptyMap(),
) {
    /**
     * The page to open, with the variables filled in (encoded as URL components): the widget's
     * own `data`, then who we are and where, then [client]'s (theme, language) over all of them.
     */
    fun url(
        roomId: String,
        userId: String?,
        displayName: String?,
        avatarMxc: String?,
        client: Map<String, String> = emptyMap(),
    ): String {
        val values =
            data +
                buildMap {
                    put("matrix_room_id", roomId)
                    put("matrix_widget_id", id)
                    userId?.let { put("matrix_user_id", it) }
                    put("matrix_display_name", displayName ?: userId.orEmpty())
                    avatarMxc?.let { put("matrix_avatar_url", it) }
                } + client
        // Longest first, so `$matrix_user_id` isn't taken for a `$matrix_user` and the rest.
        return values.keys.sortedByDescending { it.length }.fold(template) { url, key ->
            url.replace("$$key", encode(values.getValue(key)))
        }
    }

    companion object {
        const val TYPE = "im.vector.modular.widgets"
        const val SPEC_TYPE = "m.widget"

        /** The room's widgets, by name; a key set as both types counts once (as `m.widget`). */
        fun of(state: List<Event>): List<Widget> =
            state
                .filter { (it.type == TYPE || it.type == SPEC_TYPE) && it.redactedBy == null }
                .sortedBy { if (it.type == SPEC_TYPE) 0 else 1 }
                .distinctBy { it.stateKey }
                .mapNotNull(::parse)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        /** One widget's state, or null when it was removed (empty) or its URL isn't a web page. */
        fun parse(event: Event): Widget? {
            val content = event.content
            val template = content.str("url")?.trim()?.takeIf(::isWebUrl) ?: return null
            val id = event.stateKey?.takeIf { it.isNotEmpty() } ?: content.str("id") ?: return null
            val data = content.obj("data")
            val title = data?.str("title")?.takeIf { it.isNotBlank() }
            return Widget(
                id = id,
                name =
                    content.str("name")?.takeIf { it.isNotBlank() }
                        ?: title
                        ?: runCatching { URI(template).host }.getOrNull()
                        ?: id,
                type = content.str("type").orEmpty(),
                title = title,
                creator = content.str("creatorUserId") ?: event.sender,
                template = template,
                data = data?.let(::strings).orEmpty(),
            )
        }

        private fun strings(data: JsonObject): Map<String, String> =
            data
                .mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let { key to it }
                }.toMap()

        private fun isWebUrl(url: String): Boolean {
            val scheme = url.substringBefore(':', "").lowercase()
            return scheme == "https" || scheme == "http"
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}

/** A [widget] as we'd open it here: who we are fills in its page's address ([url]). */
data class RoomWidget(
    val widget: Widget,
    val roomId: String,
    val userId: String?,
    val displayName: String?,
    val avatarMxc: String?,
) {
    /** The page, with the client's own variables (theme, language) as [client] gives them. */
    fun url(client: Map<String, String> = emptyMap()): String =
        widget.url(roomId, userId, displayName, avatarMxc, client)
}
