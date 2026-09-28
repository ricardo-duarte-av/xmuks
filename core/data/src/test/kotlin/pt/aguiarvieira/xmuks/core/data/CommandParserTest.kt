package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.commands.BotCommand
import pt.aguiarvieira.xmuks.core.data.commands.CommandParser
import pt.aguiarvieira.xmuks.core.protocol.GomuksJson

/**
 * The parser against mautrix's own command test data (event/cmdschema/testdata, the same data
 * gomuks web's commands.test.ts runs) — so both clients read the same input the same way.
 */
class CommandParserTest {
    private fun resource(path: String) = GomuksJson.parseToJsonElement(javaClass.getResourceAsStream(path)!!.bufferedReader().readText())

    @Test
    fun `quoting matches mautrix's test data`() {
        val cases = resource("/cmdschema/parse_quote.json").jsonArray
        assertTrue(cases.isNotEmpty())
        cases.forEach { case ->
            case as JsonObject
            val input = (case["input"] as JsonPrimitive).content
            val expected = case["output"]!!.jsonArray
            val (value, rest, quoted) = CommandParser.parseQuoted(input)
            val name = (case["name"] as JsonPrimitive).content
            assertEquals(name, (expected[0] as? JsonPrimitive)?.contentOrNull, value)
            assertEquals(name, (expected[1] as JsonPrimitive).content, rest)
            assertEquals(name, (expected[2] as JsonPrimitive).booleanOrNull, quoted)
        }
    }

    @Test
    fun `commands parse as mautrix's test data expects`() {
        val files = listOf("flags", "room_id_or_alias", "room_reference_list", "simple", "tail")
        var count = 0
        files.forEach { file ->
            val data = resource("/cmdschema/commands/$file.json").jsonObject
            val specJson = data["spec"]!!.jsonObject
            val spec = BotCommand.parse(specJson, (specJson["source"] as JsonPrimitive).content)
            assertNotNull("$file spec", spec)
            data["tests"]!!.jsonArray.forEach { test ->
                test as JsonObject
                val name = "$file: ${(test["name"] as JsonPrimitive).content}"
                val input = (test["input"] as JsonPrimitive).content
                val expected = test["output"].takeUnless { it is JsonNull }
                assertEquals(name, expected, CommandParser.parse(spec!!, input))
                count++
            }
        }
        assertTrue(count > 10)
    }

    @Test
    fun `gomuks' built-ins load, and the longest name wins`() {
        val builtIns = BotCommand.builtIns()
        assertTrue(builtIns.size >= 20)
        assertTrue(builtIns.none { it.command == "devtools" })
        val alias = CommandParser.match("/alias add foo", builtIns)
        assertEquals("alias add", alias?.command)
        val kick = builtIns.first { it.command == "kick" }
        assertEquals(
            GomuksJson.parseToJsonElement("""{"user_id":"@bob:x.org","reason":"spamming a lot"}"""),
            CommandParser.parse(kick, "/kick @bob:x.org spamming a lot"),
        )
        assertEquals("/kick <user_id> [reason]", kick.usage)
    }
}
