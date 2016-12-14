package org.nexivo.kt.netter.events

import org.nexivo.kt.eventify.api.Event
import org.nexivo.kt.eventify.api.EventType
import org.nexivo.kt.eventify.dsl.toEnumSet
import java.util.*

enum class ServerEvent(vararg types: EventType) : Event {

    Connections (EventType.Change),
    Messages    (EventType.Message);

    final override val Type: EnumSet<EventType>

    init {
        Type = types.toEnumSet()
    }
}