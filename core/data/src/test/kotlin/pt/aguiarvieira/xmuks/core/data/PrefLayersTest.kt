package pt.aguiarvieira.xmuks.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.prefs.PrefLayers
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs

class PrefLayersTest {
    private fun layers(vararg scopes: Pair<PrefScope, Map<String, Any>>) =
        PrefLayers(
            scopes.associate { (scope, values) ->
                scope to
                    JsonObject(
                        values.mapValues { (_, v) ->
                            when (v) {
                                is Boolean -> JsonPrimitive(v)
                                is Int -> JsonPrimitive(v)
                                else -> JsonPrimitive(v.toString())
                            }
                        },
                    )
            },
        )

    @Test
    fun `the most specific scope wins, then the default`() {
        val key = Prefs.sendReadReceipts.key
        val all =
            layers(
                PrefScope.Account to mapOf(key to false),
                PrefScope.Device to mapOf(key to true),
                PrefScope.RoomAccount to mapOf(key to false),
            )
        assertEquals(false, all.get(Prefs.sendReadReceipts))
        assertEquals(PrefScope.RoomAccount, all.source(Prefs.sendReadReceipts))
        assertEquals(true, layers(PrefScope.Device to mapOf(key to true), PrefScope.Account to mapOf(key to false)).get(Prefs.sendReadReceipts))
        assertEquals(true, PrefLayers.EMPTY.get(Prefs.sendReadReceipts))
        assertNull(PrefLayers.EMPTY.source(Prefs.sendReadReceipts))
    }

    @Test
    fun `a scope a preference can't be set in is ignored`() {
        // Low bandwidth is this device's only: an account value (set by hand) doesn't count.
        val key = Prefs.lowBandwidth.key
        assertEquals(false, layers(PrefScope.Account to mapOf(key to true)).get(Prefs.lowBandwidth))
    }

    @Test
    fun `values of the wrong kind are skipped`() {
        val width = Prefs.maxImageWidth.key
        val style = Prefs.roomListStyle.key
        val odd = layers(PrefScope.Account to mapOf(width to "wide", style to "enormous"))
        assertEquals(320, odd.get(Prefs.maxImageWidth))
        assertEquals("default", odd.get(Prefs.roomListStyle))
        assertEquals(480, layers(PrefScope.Account to mapOf(width to 480)).get(Prefs.maxImageWidth))
    }
}
