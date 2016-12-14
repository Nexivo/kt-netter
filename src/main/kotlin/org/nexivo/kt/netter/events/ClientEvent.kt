package org.nexivo.kt.netter.events

import org.nexivo.kt.eventify.api.Event
import org.nexivo.kt.eventify.api.EventType
import org.nexivo.kt.eventify.dsl.toEnumSet
import java.util.*

enum class ClientEvent(vararg types: EventType) : Event {

    ID      (EventType.Change),
    Signal  (EventType.Message),
    Status  (EventType.Change);

    final override val Type: EnumSet<EventType>

    init {
        Type = types.toEnumSet()
    }
}