package org.nexivo.kt.netter

import io.vertx.core.WorkerExecutor
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetSocket
import org.nexivo.kt.eventify.EventProducer
import org.nexivo.kt.eventify.EventPublisher
import org.nexivo.kt.eventify.dsl.onError
import org.nexivo.kt.eventify.events.ChangedEvent
import org.nexivo.kt.netter.api.ConnectionException
import org.nexivo.kt.netter.api.ServerAction
import org.nexivo.kt.netter.api.ServerState
import org.nexivo.kt.netter.api.types.ServerDefinitions
import org.nexivo.kt.netter.api.types.ServerEvents
import org.nexivo.kt.netter.api.types.ServerStateMachine
import org.nexivo.kt.netter.api.types.ServerStates
import org.nexivo.kt.netter.dsl.VERTX
import org.nexivo.kt.netter.dsl.server
import org.nexivo.kt.netter.dsl.setup
import org.nexivo.kt.netter.events.ServerEvent
import org.nexivo.kt.netter.events.ServerMessage
import org.nexivo.kt.stately.StateDefinitions.Companion.states
import org.nexivo.kt.stately.StateMachine.Companion.machine
import org.nexivo.kt.stately.api.StateHandlerResult
import org.nexivo.kt.stately.api.Stateful
import java.io.Closeable
import java.net.InetAddress
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

class Server(
        private val poolSize:   Int     = DEFAULT_EXECUTOR_POOL_SIZE,
                    sharedPool: Boolean = DEFAULT_TO_INDIVIDUAL_CLIENT_POOLS,
        private val emmitID:    Boolean = DEFAULT_TO_EMITTING_IDS
    ) : EventProducer<Server, ServerEvent>,
        Stateful<ServerState>,
        Closeable {

    private typealias Clients              = MutableMap<String, Connection>
    private typealias ConnectionHandler    = (String, Buffer) -> String?
    private typealias NewClientIDHandler   = () -> String
    private typealias NewConnectionHandler = (String) -> Unit
    private typealias ServerChangeEvent    = ChangedEvent<Server, Int, ServerEvent>
    private typealias UnitHandler          = () -> Unit

    companion object {

        internal const val DEFAULT_EXECUTOR_POOL_SIZE:         Int     = 10
        internal const val DEFAULT_TO_INDIVIDUAL_CLIENT_POOLS: Boolean = false
        internal const val DEFAULT_TO_EMITTING_IDS:            Boolean = true

        private val DEFAULT_NEW_CLIENT_ID_HANDLER: NewClientIDHandler = { UUID.randomUUID().toString() }

        private val SERVER_STATES: ServerDefinitions
            = states {

                initialState { ServerState.Idle }

                startsWith   { ServerAction.Stop }

                ServerState.Idle transitions {

                    ServerAction.Start triggers ServerState.Running

                    ServerAction.Stop  triggers ServerState.Stopped
                }

                ServerState.Stopped transitions {

                    ServerAction.Start triggers ServerState.Running
                }

                ServerState.Running transitions {

                    ServerAction.NewClient triggers ServerState.ServingClients

                    ServerAction.NoClients triggers ServerState.WaitingForClients
                }

                ServerState.Paused transitions {

                    ServerAction.Restart triggers ServerState.Running

                    ServerAction.Stop    triggers ServerState.Stopped
                }

                ServerState.ServingClients transitions {

                    ServerAction.Exception triggers ServerState.Error

                    ServerAction.NewClient triggers ServerState.ServingClients

                    ServerAction.NoClients triggers ServerState.WaitingForClients

                    ServerAction.Pause     triggers ServerState.Paused

                    ServerAction.Restart   triggers ServerState.Stopped

                    ServerAction.Stop      triggers ServerState.Stopped
                }

                ServerState.WaitingForClients transitions {

                    ServerAction.Exception triggers ServerState.Error

                    ServerAction.Pause     triggers ServerState.Paused

                    ServerAction.NewClient triggers ServerState.ServingClients

                    ServerAction.Restart   triggers ServerState.Stopped

                    ServerAction.Stop      triggers ServerState.Stopped
                }
            }
    }

    inner private class Connection
        internal constructor(val id: String, private val client: NetSocket)
        : Closeable {

        private val _pool: WorkerExecutor = _sharedPool ?: VERTX.createSharedWorkerExecutor("client-$id-worker-pool", poolSize)
        private var _connected: AtomicBoolean = AtomicBoolean(true)

        init {
            client setup {

                closeHandler {

                    _connected.set(false)

                    synchronized(_clients) {
                        _clients.remove(id)

                        ServerEvent.Connections changed _clients.size
                    }

                    ServerMessage.Output message "DISCONNECTED: $id"
                }

                exceptionHandler {
                    ex ->

                    _state trigger ServerAction.Exception

                    onError(ConnectionException(id, ex))

                    close()
                }

                handler {
                    buffer ->

                    _pool.executeBlocking<String?>({
                        it.complete(_handleConnection?.invoke(id, buffer))
                    }, {
                        val result: String? = it.result()

                        if (result != null && _connected.get()) {
                            write(result)
                        }
                    })
                }

                if (emmitID) {
                    write("ID:$id")
                }
            }
        }

        fun pause(): Unit {
            client.pause()
        }

        fun resume(): Unit {
            client.resume()
        }

        fun write(data: String): Unit {

            client.write(data)
        }

        override fun close() {

            client.close()

            if (_pool != _sharedPool) {
                _pool.close()
            }
        }
    }

    private val _sharedPool: WorkerExecutor? = if (sharedPool) { VERTX.createSharedWorkerExecutor("server-worker-pool", poolSize) } else { null }

    private val _server: NetServer
    private val _clients: Clients = Collections.synchronizedMap(LinkedHashMap())

    private var _host: InetAddress = InetAddress.getLocalHost()
    private var _port:   Int         = 0

    private var _handleConnection:     ConnectionHandler?    = null
    private var _handlerNewClientID:   NewClientIDHandler?   = null
    private var _handlerNewConnection: NewConnectionHandler? = null
    private var _handlerPause:         UnitHandler?          = null
    private var _handlerStart:         UnitHandler?          = null
    private var _handlerStop:          UnitHandler?          = null

    private val _state: ServerStateMachine

    override val events: ServerEvents by lazy { EventPublisher.create<Server, ServerEvent>() }

    init {

        _server = server {

            connectHandler {

                client ->

                val id: String = (_handlerNewClientID ?: DEFAULT_NEW_CLIENT_ID_HANDLER).invoke()

                synchronized(_clients) {

                    _clients.put(id, Connection(id, client))

                    _handlerNewConnection?.invoke(id)

                    ServerEvent.Connections changed _clients.size
                }

                ServerMessage.Output message "CONNECTED: $id"
            }
        }

        _state = machine {

            definitions { SERVER_STATES }

            ServerState.Stopped state {

                onEnter {
                    (trigger: ServerAction) ->

                    synchronized(_clients) {
                        _clients.values.forEach(Connection::close)
                    }

                    var result: StateHandlerResult = StateHandlerResult.Continue

                    _server.close {

                        if (it.succeeded()) {

                            _handlerStop?.invoke()

                            ServerMessage.Output message "Server ${_host.hostAddress}:$_port stopped!"
                        } else {
                            result = StateHandlerResult.Exception

                            ServerMessage.Output message "Failed to stop server ${_host.hostAddress}:$_port!"
                        }
                    }

                    if (trigger == ServerAction.Restart && result == StateHandlerResult.Continue) {
                        this@machine trigger ServerAction.Start
                    }

                    result
                }
            }

            ServerState.Running state {

                onEnter(from = ServerState.Stopped) {

                    var result: StateHandlerResult = StateHandlerResult.Continue
                    val wait:   Semaphore          = Semaphore(1)

                    wait.acquire()

                    _server.listen(_port, _host.hostAddress) {

                        if (it.succeeded()) {

                            _handlerStart?.invoke()

                            ServerMessage.Output message "Start server ${_host.hostAddress}:$_port!"
                        } else {
                            ServerMessage.Output message "Failed to start server ${_host.hostAddress}:$_port!"

                            result = StateHandlerResult.Exception
                        }

                        wait.release()
                    }

                    wait.acquire()

                    if (result == StateHandlerResult.Continue) {
                        ServerEvent.Connections changed _clients.size
                    }

                    result
                }

                onEnter(from = ServerState.Paused) {

                    var result: StateHandlerResult = StateHandlerResult.Continue

                    try {
                        synchronized(_clients) {
                            _clients.values.forEach(Connection::resume)
                        }

                        ServerEvent.Connections changed _clients.size

                        ServerMessage.Output message "Resumed all connections for server ${_host.hostAddress}:$_port!"
                    } catch (ex: Throwable) {
                        ServerMessage.Output message "Failed to resume connections for server ${_host.hostAddress}:$_port!"

                        result = StateHandlerResult.Exception
                    }

                    result
                }
            }

            ServerState.Paused state {

                onEnter {

                    var result: StateHandlerResult = StateHandlerResult.Continue

                    try {
                        synchronized(_clients) {
                            _clients.values.forEach(Connection::pause)
                        }

                        _handlerPause?.invoke()

                        ServerMessage.Output message "Paused remote connections for server ${_host.hostAddress}:$_port!"
                    } catch (ex: Throwable) {
                        ServerMessage.Output message "Failed to pause remote connections for server ${_host.hostAddress}:$_port!"

                        result = StateHandlerResult.Exception
                    }

                    result
                }
            }


            this@Server.events.filter { it.event == ServerEvent.Connections } react {

                @Suppress("UNCHECKED_CAST")
                if ((it as ServerChangeEvent).newValue!! > 0) { ServerAction.NewClient } else { ServerAction.NoClients }
            }
        }
    }

    override val state: ServerStates by lazy { _state }

    private operator fun get(id: String): Connection? = _clients[id]

    fun newClientID(handler: NewClientIDHandler?): Server {

        _handlerNewClientID = handler

        return this
    }

    fun onNewConnection(handler: NewConnectionHandler): Server {

        _handlerNewConnection = handler

        return this
    }

    fun onPause(handler: UnitHandler): Server {

        _handlerPause = handler

        return this
    }

    fun onReceive(handler: ConnectionHandler): Server {

        _handleConnection = handler

        return this
    }

    fun onStart(handler: UnitHandler): Server {

        _handlerStart = handler

        return this
    }

    fun onStop(handler: UnitHandler): Server {

        _handlerStop = handler

        return this
    }

    fun broadcast(data: String, predicate: (String) -> Boolean = { true }): Int {

        var writes: Int = 0

        synchronized(_clients) {
            _clients.forEach {
                id, client ->

                if (predicate(id)) {
                    client.write(data)

                    writes++
                }
            }
        }

        return writes
    }

    fun activate(): Unit {

        _state.initiate()
    }

    fun pause (): Unit {

        _state trigger ServerAction.Pause
    }

    fun restart (): Unit {

        _state trigger ServerAction.Restart
    }

    fun start(port: Int, host: InetAddress): Unit {

        if (_state `can trigger` ServerAction.Start) {
            _port = port
            _host = host

            _state trigger ServerAction.Start
        }
    }

    fun stop (): Unit {

        _state trigger ServerAction.Stop
    }

    override fun close(): Unit {

        stop ()

        _sharedPool?.close()
    }
}
