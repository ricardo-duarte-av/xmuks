package pt.aguiarvieira.xmuks.core.data.commands

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand.Schema

/**
 * Turns typed text into a command's arguments — a port of gomuks web's `stringToCommandArgs`
 * (web/src/api/types/commands.ts), so the same input means the same thing in both clients:
 * positional arguments, `"quoted strings"` with `\` escapes, `<array items>`, `--named=value`,
 * a tail parameter that takes the rest of the line, and Matrix IDs / matrix.to / matrix: links.
 * Checked against mautrix's own command test data.
 */
object CommandParser {
    /** The command [text] invokes among [commands] (longest name first: `/alias add` over `/alias`). */
    fun match(
        text: String,
        commands: List<BotCommand>,
    ): BotCommand? =
        commands
            .sortedByDescending { it.command.length }
            .firstOrNull { prefixLength(it, text) != null }

    /**
     * [text]'s arguments for [spec], or null when [text] doesn't invoke it. Arguments that don't
     * parse fall back to their default (or null when optional), like gomuks web.
     */
    fun parse(
        spec: BotCommand,
        text: String,
    ): JsonObject? {
        val start = prefixLength(spec, text) ?: return null
        return ArgumentReader(spec, text.substring(start)).read()
    }

    /** How long `/command` (plus a space, or the bot's ID for disambiguation) is in [text]. */
    private fun prefixLength(
        spec: BotCommand,
        text: String,
    ): Int? =
        (listOf(spec.command) + spec.aliases).firstNotNullOfOrNull { name ->
            val prefix = "/$name"
            if (text.startsWith(prefix)) {
                afterPrefix(text.substring(prefix.length), spec.source)?.let {
                    prefix.length +
                        it
                }
            } else {
                null
            }
        }

    private fun afterPrefix(
        rest: String,
        source: String,
    ): Int? =
        when {
            rest.isEmpty() -> 0
            rest.startsWith(" ") -> 1
            rest == source -> source.length
            rest.startsWith("$source ") -> source.length + 1
            else -> null
        }

    /** Reads one word, or one `"quoted \"string\""`. Returns (value, rest, wasQuoted). */
    internal fun parseQuoted(value: String): Triple<String?, String, Boolean> {
        if (value.isEmpty()) return Triple("", "", false)
        if (!value.startsWith('"')) {
            val space = value.indexOf(' ')
            return if (space <
                0
            ) {
                Triple(value, "", false)
            } else {
                Triple(value.substring(0, space), value.substring(space).trimStart(), false)
            }
        }
        return readQuoted(value.substring(1))
    }

    /** The inside of a quoted string: `\x` escapes x; an unterminated quote runs to the end. */
    private fun readQuoted(start: String): Triple<String?, String, Boolean> {
        var rest = start
        val out = StringBuilder()
        var escaped = false
        var closed = false
        while (!closed) {
            val quote = rest.indexOf('"')
            val escape = rest.substring(0, if (quote < 0) rest.length else quote).indexOf('\\')
            if (escape >= 0) {
                out.append(rest, 0, escape)
                if (escape + 1 < rest.length) out.append(rest[escape + 1])
                rest = rest.substring(minOf(escape + 2, rest.length))
                escaped = true
            } else {
                if (quote < 0 && !escaped) return Triple(rest, "", true)
                val end = if (quote < 0) rest.length else quote
                out.append(rest, 0, end)
                rest = if (quote < 0) "" else rest.substring(quote + 1)
                closed = true
            }
        }
        return Triple(out.toString(), rest.trimStart(), true)
    }

    internal fun toArgument(
        schema: Schema,
        value: String?,
    ): JsonElement? {
        value ?: return null
        return when (schema) {
            is Schema.Literal -> schema.value.takeIf { literalEquals(it, value) }
            is Schema.Primitive -> primitive(schema.type, value)
            is Schema.Union -> schema.variants.firstNotNullOfOrNull { toArgument(it, value) }
            is Schema.Array -> toArgument(schema.items, value)
        }
    }

    private fun primitive(
        type: String,
        value: String,
    ): JsonElement? =
        when (type) {
            "string" -> JsonPrimitive(value)
            "boolean" -> parseBoolean(value)?.let(::JsonPrimitive)
            "integer" -> value.trim().toLongOrNull()?.let(::JsonPrimitive)
            "server_name" -> value.takeIf { SERVER_NAME.matches(it) }?.let(::JsonPrimitive)
            else -> MatrixIdentifiers.parse(type, value)
        }

    private fun literalEquals(
        expected: JsonElement,
        value: String,
    ): Boolean {
        if (expected is JsonObject) {
            val type = (expected["type"] as? JsonPrimitive)?.contentOrNull ?: return false
            return MatrixIdentifiers.parse(type, value) == expected
        }
        val primitive = expected as? JsonPrimitive ?: return false
        return when {
            primitive.isString -> primitive.content == value
            primitive.booleanOrNull != null -> parseBoolean(value) == primitive.booleanOrNull
            else -> primitive.longOrNull != null && value.trim().toLongOrNull() == primitive.longOrNull
        }
    }

    private fun parseBoolean(value: String): Boolean? =
        when (value.lowercase()) {
            "t", "true", "y", "yes", "1" -> true
            "f", "false", "n", "no", "0" -> false
            else -> null
        }

    internal fun BotCommand.Parameter.default(): JsonElement =
        defaultValue ?: if (optional) JsonNull else schema.emptyValue()

    private fun Schema.emptyValue(): JsonElement =
        when (this) {
            is Schema.Literal -> {
                value
            }

            is Schema.Primitive -> {
                when (type) {
                    "boolean" -> JsonPrimitive(false)
                    "integer" -> JsonPrimitive(0)
                    else -> JsonPrimitive("")
                }
            }

            is Schema.Union -> {
                variants.first().emptyValue()
            }

            is Schema.Array -> {
                JsonArray(emptyList())
            }
        }

    internal fun Schema.allows(primitive: String): Boolean =
        when (this) {
            is Schema.Primitive -> type == primitive
            is Schema.Union -> variants.any { it.allows(primitive) }
            is Schema.Array -> items.allows(primitive)
            is Schema.Literal -> false
        }

    private val SERVER_NAME = Regex("""^[A-Za-z0-9.\-]+(:\d{1,5})?$|^\[[0-9A-Fa-f:.]+](:\d{1,5})?$""")
}
