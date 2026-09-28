package pt.aguiarvieira.xmuks.core.data.commands

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand.Schema
import pt.aguiarvieira.xmuks.core.data.commands.CommandParser.allows
import pt.aguiarvieira.xmuks.core.data.commands.CommandParser.default
import pt.aguiarvieira.xmuks.core.data.commands.CommandParser.parseQuoted
import pt.aguiarvieira.xmuks.core.data.commands.CommandParser.toArgument

/** One pass over a command's argument text; [input] is what's left to read. */
internal class ArgumentReader(
    private val spec: BotCommand,
    private var input: String,
) {
    private val args = LinkedHashMap<String, JsonElement>()
    private val named = BooleanArray(spec.parameters.size)

    /** Where a parameter sits, which changes how much of the input it may take. */
    private class Position(
        val last: Boolean,
        val tail: Boolean,
        val named: Boolean,
    )

    fun read(): JsonObject {
        spec.parameters.forEachIndexed { index, param ->
            readNamed()
            val tail = param.key == spec.tailParameter
            // Optional parameters are only filled positionally when they're the tail one.
            if (named[index] || (param.optional && !tail)) return@forEachIndexed
            read(param, Position(last = index == spec.parameters.lastIndex, tail = tail, named = false))
        }
        return JsonObject(args)
    }

    /** `--key=value` / `--key value` / `--flag`, anywhere before a positional parameter. */
    private fun readNamed() {
        while (input.startsWith("--")) {
            val end = listOf(input.indexOf('='), input.indexOf(' ')).filter { it >= 0 }.minOrNull() ?: input.length
            val target = spec.parameters.indexOfFirst { it.key == input.substring(2, end) }
            if (target < 0) return
            input = input.substring(end).removePrefix("=")
            named[target] = true
            read(spec.parameters[target], Position(last = false, tail = false, named = true))
        }
    }

    private fun read(
        param: BotCommand.Parameter,
        at: Position,
    ) {
        val schema = param.schema
        if (schema is Schema.Array) {
            val values = readArray(schema, at.last)
            args[param.key] = if (values.isNotEmpty()) JsonArray(values) else param.default()
        } else {
            readScalar(param, schema, at)
        }
    }

    private fun readScalar(
        param: BotCommand.Parameter,
        schema: Schema,
        at: Position,
    ) {
        val before = input
        var (value, rest, quoted) = parseQuoted(input)
        input = rest
        val takesRest = at.last || at.tail
        if (takesRest && !quoted && input.isNotEmpty()) {
            // An unquoted last argument takes the rest of the line as is.
            value += " $input"
            input = ""
        }
        val bareFlag = value.isNullOrEmpty() && !quoted
        if (bareFlag && at.named && schema.allows("boolean")) {
            args[param.key] = JsonPrimitive(true)
            return
        }
        val parsed = toArgument(schema, value)
        args[param.key] = parsed ?: param.default()
        // An optional parameter that didn't fit leaves its text for the next one.
        val positional = !at.last && !at.named
        if (parsed == null && param.optional && positional) input = before.trimStart()
    }

    private fun readArray(
        schema: Schema.Array,
        last: Boolean,
    ): List<JsonElement> {
        val opened = input.startsWith(OPEN)
        var closed = false
        if (opened) {
            input = input.substring(1)
            if (input.startsWith(CLOSE)) {
                input = input.substring(1).trimStart()
                closed = true
            }
        }
        val out = ArrayList<JsonElement>()
        while (input.isNotEmpty() && !closed) {
            var (value, rest, quoted) = parseQuoted(input)
            input = rest
            val endsWithClose = !quoted && value?.endsWith(CLOSE) == true
            when {
                opened && endsWithClose -> {
                    value = value.dropLast(1)
                    closed = true
                }

                opened && input.startsWith(CLOSE) -> {
                    input = input.substring(1).trimStart()
                    closed = true
                }

                // Without <>, an array in the middle takes one item.
                !opened && !last -> {
                    closed = true
                }
            }
            toArgument(schema.items, value)?.let(out::add)
        }
        return out
    }

    private companion object {
        const val OPEN = "<"
        const val CLOSE = ">"
    }
}
