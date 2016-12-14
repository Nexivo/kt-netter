package org.nexivo.kt.netter.api.types

import org.nexivo.kt.eventify.EventPublisher
import org.nexivo.kt.netter.Server
import org.nexivo.kt.netter.api.ServerAction
import org.nexivo.kt.netter.api.ServerState
import org.nexivo.kt.netter.events.ServerEvent
import org.nexivo.kt.stately.StateDefinitions
import org.nexivo.kt.stately.StateMachine
import rx.Observable

internal typealias ServerDefinitions  = StateDefinitions<ServerState, ServerAction>
internal typealias ServerStateMachine = StateMachine<ServerState, ServerAction>

typealias ServerEvents = EventPublisher<Server, ServerEvent>
typealias ServerStates = Observable<ServerState>
