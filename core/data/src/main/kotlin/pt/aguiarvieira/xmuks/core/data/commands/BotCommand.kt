package pt.aguiarvieira.xmuks.core.data.commands

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * A slash command (MSC4391): gomuks' built-ins (run by gomuks itself, [source] `@gomuks`) and the
 * ones bots publish in room state (`org.matrix.msc4391.command_description`, [source] = the bot).
 * A structured command is sent as a message addressed to [source], with typed arguments.
 */
data class BotCommand(
    val command: String,
    val source: String,
    val aliases: List<String> = emptyList(),
    val parameters: List<Parameter> = emptyList(),
    val description: String? = null,
    /** The parameter that swallows the rest of the line unquoted (e.g. a reason). */
    val tailParameter: String? = null,
    /**
     * A text prefix gomuks parses out of the message itself (`/me`, `/notice`, `/rainbow`…),
     * rather than a structured command.
     */
    val textPrefix: Boolean = false,
) {
    data class Parameter(
        val key: String,
        val schema: Schema,
        val optional: Boolean = false,
        val description: String? = null,
        val defaultValue: JsonElement? = null,
    )

    /** What a parameter accepts. */
    sealed interface Schema {
        data class Primitive(
            val type: String,
        ) : Schema

        /** Exactly this value (a string, number, boolean or room/event reference). */
        data class Literal(
            val value: JsonElement,
        ) : Schema

        data class Union(
            val variants: List<Schema>,
        ) : Schema

        data class Array(
            val items: Schema,
        ) : Schema
    }

    /** What to show while typing it: `/kick <user_id> [reason]`. */
    val usage: String
        get() =
            buildString {
                append('/').append(command)
                parameters.forEach { append(' ').append(if (it.optional) "[${it.key}]" else "<${it.key}>") }
            }

    companion object {
        const val GOMUKS = "@gomuks"
        const val STATE_TYPE = "org.matrix.msc4391.command_description"

        /** Parses a command description; null when it isn't a valid MSC4391 command. */
        fun parse(
            content: JsonObject,
            source: String,
        ): BotCommand? {
            val command = content.string("command")?.takeIf { it.isNotBlank() } ?: return null
            val parameters =
                (content["parameters"] as? JsonArray)?.map {
                    parseParameter(it as? JsonObject ?: return null) ?: return null
                } ?: emptyList()
            return BotCommand(
                command = command,
                source = source,
                aliases =
                    (content["aliases"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .orEmpty(),
                parameters = parameters,
                description = extensibleText(content["description"]),
                tailParameter = content.string("fi.mau.tail_parameter"),
            )
        }

        /** gomuks' built-ins, as bundled from its source (see scripts/update-gomuks-commands.sh). */
        fun builtIns(): List<BotCommand> {
            val json =
                BotCommand::class.java
                    .getResourceAsStream(
                        "/gomuks-commands.json"
                    )?.bufferedReader()
                    ?.use { it.readText() }
                    ?: return emptyList()
            return GomuksJson
                .parseToJsonElement(json)
                .jsonArray
                .mapNotNull { (it as? JsonObject)?.let { spec -> parse(spec, GOMUKS) } }
                .filter { it.command !in CLIENT_ONLY }
        }

        /** The text prefixes gomuks handles in any message (gomuks web's "fake commands"). */
        val textPrefixes: List<BotCommand> =
            listOf(
                "plain" to "Send a plain text message without any formatting",
                "html" to "Send a formatted message with only HTML (no markdown)",
                "rainbow" to "Send a message with rainbow colors (markdown allowed)",
                "htmlmd" to "Send a formatted message that allows both HTML and markdown",
                "me" to "Send an m.emote message",
                "notice" to "Send an m.notice message",
                "unencrypted" to "Send an unencrypted message even if the room is encrypted",
                "rawinputbody" to "Use the input text as the body field as-is",
            ).map { (name, description) ->
                BotCommand(
                    command = name,
                    source = GOMUKS,
                    parameters = listOf(Parameter("text", Schema.Primitive("string"))),
                    description = description,
                    textPrefix = true,
                )
            }

        /** Built-ins only gomuks web implements (a web-only screen), not gomuks. */
        private val CLIENT_ONLY = setOf("devtools")

        private fun parseParameter(obj: JsonObject): Parameter? {
            val key = obj.string("key") ?: return null
            val schema = parseSchema(obj["schema"] as? JsonObject ?: return null, parent = null) ?: return null
            return Parameter(
                key = key,
                schema = schema,
                optional = (obj["optional"] as? JsonPrimitive)?.booleanOrNull == true,
                description = extensibleText(obj["description"]),
                defaultValue = obj["fi.mau.default_value"],
            )
        }

        /** As gomuks web validates it: unions only at the top or in arrays, arrays only at the top. */
        private fun parseSchema(
            obj: JsonObject,
            parent: String?,
        ): Schema? =
            when (obj.string("schema_type")) {
                "primitive" -> {
                    obj.string("type")?.takeIf { it in PRIMITIVES }?.let(Schema::Primitive)
                }

                "literal" -> {
                    obj["value"]?.let(Schema::Literal)
                }

                "union" -> {
                    if (parent != null && parent != "array") return null
                    val variants =
                        (obj["variants"] as? JsonArray)?.map {
                            parseSchema(it as? JsonObject ?: return null, "union")
                                ?: return null
                        }
                    variants?.let(Schema::Union)
                }

                "array" -> {
                    if (parent != null) return null
                    (obj["items"] as? JsonObject)?.let { parseSchema(it, "array") }?.let(Schema::Array)
                }

                else -> {
                    null
                }
            }

        private val PRIMITIVES =
            setOf("string", "integer", "boolean", "server_name", "user_id", "room_id", "room_alias", "event_id")

        private fun extensibleText(element: JsonElement?): String? {
            val texts =
                ((element as? JsonObject)?.get("m.text") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
            return texts
                .firstOrNull {
                    it.string("mimetype").let { m ->
                        m == null || m == "text/plain"
                    }
                }?.string("body")
        }

        private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}
