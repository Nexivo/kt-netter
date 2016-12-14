package org.nexivo.kt.netter.api

import org.nexivo.kt.stately.api.Stateful
import org.nexivo.kt.stately.api.States
import org.nexivo.kt.stately.dsl.ObservedState

class ServerObserver(override val source: Stateful<ServerState>) : States<ServerState>() {

    val failed: ObservedState by WhenStateIs(ServerState.Error)

    val serving: ObservedState by WhenStateIs(ServerState.ServingClients)

    val waiting: ObservedState by WhenStateIs(ServerState.WaitingForClients)

    val paused: ObservedState by WhenStateIs(ServerState.Paused)

    val running: ObservedState by WhenStateIs(ServerState.Running)

    val stopped: ObservedState by WhenStateIs(ServerState.Stopped)
}