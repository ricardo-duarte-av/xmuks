package pt.aguiarvieira.xmuks.feature.room

/** A place picked to send: where the pin was, and whether that's where we are. */
data class PickedLocation(
    val latitude: Double,
    val longitude: Double,
    /** Metres, when it came from the phone's location. */
    val accuracy: Float?,
    /** Our own position (MSC3488 `m.self`), not a pin dropped elsewhere (`m.pin`). */
    val self: Boolean,
)
