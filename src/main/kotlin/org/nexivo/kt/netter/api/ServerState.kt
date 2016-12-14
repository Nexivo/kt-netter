package org.nexivo.kt.netter.api

import org.nexivo.kt.stately.api.State
import org.nexivo.kt.stately.api.StateType
import org.nexivo.kt.stately.api.types.CompositeStates

enum class ServerState(override val type: StateType, vararg compositeStates: ServerState) : State<ServerState> {

    Idle              (StateType.Initial),
    Error             (StateType.Final),
    Paused            (StateType.State),
    Shutdown          (StateType.Final),
    ServingClients    (StateType.State),
    Stopped           (StateType.State),
    WaitingForClients (StateType.State),
    Running           (StateType.Composite, ServingClients, WaitingForClients);

    override val compositeOf: CompositeStates<ServerState>?
        =   if (compositeStates.isNotEmpty()) arrayOf(*compositeStates) else null
}