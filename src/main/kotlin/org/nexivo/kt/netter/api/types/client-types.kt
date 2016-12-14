package org.nexivo.kt.netter.api.types

import org.nexivo.kt.eventify.EventPublisher
import org.nexivo.kt.netter.Client
import org.nexivo.kt.netter.api.ClientAction
import org.nexivo.kt.netter.api.ClientState
import org.nexivo.kt.netter.events.ClientEvent
import org.nexivo.kt.stately.StateDefinitions
import org.nexivo.kt.stately.StateMachine
import rx.Observable

internal typealias ClientDefinitions  = StateDefinitions<ClientState, ClientAction>
internal typealias ClientStateMachine = StateMachine<ClientState, ClientAction>

typealias ClientEvents = EventPublisher<Client, ClientEvent>
typealias ClientStates = Observable<ClientState>