package org.nexivo.kt.netter.events

import org.nexivo.kt.eventify.api.EventType
import org.nexivo.kt.eventify.api.Message
import org.nexivo.kt.eventify.api.MessagePriority
import java.util.*

enum class ServerMessage(override val priority: MessagePriority): Message {

    Output (MessagePriority.Info);

    override val Type: EnumSet<EventType> = EnumSet.of(EventType.Message)
}