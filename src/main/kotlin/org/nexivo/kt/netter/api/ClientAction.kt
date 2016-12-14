package org.nexivo.kt.netter.api

import org.nexivo.kt.stately.api.Action

internal enum class ClientAction : Action {

    Connect,
    Disconnect,
    Exception,
    Ready;
}