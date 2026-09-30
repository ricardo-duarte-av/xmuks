package pt.aguiarvieira.xmuks.core.data.timeline

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import pt.aguiarvieira.xmuks.core.protocol.Event

/** How many messages the server says this root's thread has (`unsigned.m.relations.m.thread.count`). */
internal fun Event.threadCount(): Int =
    ((unsigned?.obj("m.relations")?.obj(THREAD)?.get("count")) as? JsonPrimitive)?.intOrNull ?: 0
