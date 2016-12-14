package org.nexivo.kt.netter.dsl

import io.vertx.core.Vertx
import io.vertx.core.net.NetClient
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetSocket

internal val VERTX: Vertx = Vertx.vertx()

fun client (init: NetClient.() -> Unit): NetClient {

    val result: NetClient = VERTX.createNetClient()

    result.init()

    return result
}

fun server (init: NetServer.() -> Unit): NetServer {

    val result: NetServer = VERTX.createNetServer()

    result.init()

    return result
}

infix fun NetSocket.setup (init: NetSocket.() -> Unit): Unit {

    this.init()
}

fun finalizeVertx(): Unit {

    VERTX.close()
}