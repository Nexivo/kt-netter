package org.nexivo.kt.netter.api

import org.nexivo.kt.stately.api.State
import org.nexivo.kt.stately.api.StateType
import org.nexivo.kt.stately.api.types.CompositeStates

enum class ClientState(override val type: StateType) : State<ClientState> {

    Idle         (StateType.Initial),
    Connected    (StateType.State),
    Disconnected (StateType.Final),
    Exception    (StateType.Final);

    override val compositeOf: CompositeStates<ClientState>? = null
}