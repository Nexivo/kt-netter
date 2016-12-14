package org.nexivo.kt.netter.api

import org.nexivo.kt.stately.api.Action

internal enum class ServerAction : Action {
    Exception,
    NewClient,
    NoClients,
    Pause,
    Restart,
    Start,
    Stop
}

