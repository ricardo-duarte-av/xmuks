package pt.aguiarvieira.xmuks.feature.call

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.call.system.AudioRoute
import pt.aguiarvieira.xmuks.core.call.system.RouteKind

class EarHandoverTest {
    private val earpiece = AudioRoute("1", "Phone", RouteKind.Earpiece)
    private val speaker = AudioRoute("2", "Speaker", RouteKind.Speaker)
    private val bluetooth = AudioRoute("3", "Buds", RouteKind.Bluetooth)
    private val routes = listOf(earpiece, speaker, bluetooth)

    @Test
    fun `a video call on the speaker goes to the earpiece without the camera, and comes back`() {
        val handover = EarHandover()
        assertEquals(EarHandover.Change(earpiece, false), handover.near(speaker, routes, cameraOn = true))
        assertEquals(EarHandover.Change(speaker, true), handover.far(earpiece, routes, cameraOn = false))
    }

    @Test
    fun `an audio call already on the earpiece is left alone`() {
        val handover = EarHandover()
        assertEquals(EarHandover.Change(), handover.near(earpiece, routes, cameraOn = false))
        assertEquals(EarHandover.Change(), handover.far(earpiece, routes, cameraOn = false))
    }

    @Test
    fun `headphones keep the audio`() {
        val handover = EarHandover()
        assertEquals(EarHandover.Change(camera = false), handover.near(bluetooth, routes, cameraOn = true))
        assertEquals(EarHandover.Change(camera = true), handover.far(bluetooth, routes, cameraOn = false))
    }

    @Test
    fun `what changed at the ear stays changed`() {
        val handover = EarHandover()
        handover.near(speaker, routes, cameraOn = true)
        // Buds connected and the camera came back on meanwhile.
        assertEquals(EarHandover.Change(), handover.far(bluetooth, routes, cameraOn = true))
    }

    @Test
    fun `without an earpiece the speaker stays`() {
        val handover = EarHandover()
        assertEquals(EarHandover.Change(), handover.near(speaker, listOf(speaker), cameraOn = false))
        assertEquals(EarHandover.Change(), handover.far(speaker, listOf(speaker), cameraOn = false))
    }
}
