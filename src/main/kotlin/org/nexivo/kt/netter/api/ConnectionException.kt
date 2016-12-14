package org.nexivo.kt.netter.api

class ConnectionException(val id: String, cause: Throwable) : Throwable(cause)