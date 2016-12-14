package org.nexivo.kt.netter.dsl

import org.nexivo.kt.netter.Server
import org.nexivo.kt.netter.Server.Companion.DEFAULT_EXECUTOR_POOL_SIZE
import org.nexivo.kt.netter.Server.Companion.DEFAULT_TO_EMITTING_IDS
import org.nexivo.kt.netter.Server.Companion.DEFAULT_TO_INDIVIDUAL_CLIENT_POOLS

fun serve(poolSize:   Int     = DEFAULT_EXECUTOR_POOL_SIZE,
          sharedPool: Boolean = DEFAULT_TO_INDIVIDUAL_CLIENT_POOLS,
          emmitID:    Boolean = DEFAULT_TO_EMITTING_IDS, init: Server.() -> Unit): Server {

    val result: Server = Server(poolSize, sharedPool, emmitID)

    result.init()

    return result;
}
