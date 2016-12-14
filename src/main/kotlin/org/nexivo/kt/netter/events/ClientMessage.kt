package org.nexivo.kt.netter.events

import org.nexivo.kt.eventify.api.EventType
import org.nexivo.kt.eventify.api.Message
import org.nexivo.kt.eventify.api.MessagePriority
import java.util.*

enum class ClientMessage(override val priority: MessagePriority): Message {

    Output       (MessagePriority.Trace),
    DataReceived (MessagePriority.Message);

    override val Type: EnumSet<EventType> = EnumSet.of(EventType.Message)

}