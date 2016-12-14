package org.nexivo.kt.netter

import io.vertx.core.net.NetClient
import io.vertx.core.net.NetSocket
import org.nexivo.kt.eventify.EventProducer
import org.nexivo.kt.eventify.EventPublisher
import org.nexivo.kt.eventify.dsl.onError
import org.nexivo.kt.netter.api.ClientAction
import org.nexivo.kt.netter.api.ClientState
import org.nexivo.kt.netter.api.types.ClientDefinitions
import org.nexivo.kt.netter.api.types.ClientEvents
import org.nexivo.kt.netter.api.types.ClientStateMachine
import org.nexivo.kt.netter.api.types.ClientStates
import org.nexivo.kt.netter.dsl.client
import org.nexivo.kt.netter.dsl.setup
import org.nexivo.kt.netter.events.ClientEvent
import org.nexivo.kt.netter.events.ClientMessage
import org.nexivo.kt.stately.StateDefinitions.Companion.states
import org.nexivo.kt.stately.StateMachine.Companion.machine
import org.nexivo.kt.stately.api.StateHandlerResult
import org.nexivo.kt.stately.api.Stateful
import org.nexivo.kt.stately.dsl.or
import java.io.Closeable
import java.net.InetAddress

class Client(port: Int, host: InetAddress)
    : EventProducer<Client, ClientEvent>,
        Stateful<ClientState>,
        Closeable {

    companion object {

        private val CLIENT_STATES: ClientDefinitions
            = states {

                initialState { ClientState.Idle }

                startsWith   { ClientAction.Connect }

                ClientState.Idle transitions {

                    ClientAction.Connect triggers ClientState.Connected
                }

                ClientState.Connected transitions {

                    ClientAction.Disconnect triggers ClientState.Disconnected

                    ClientAction.Exception  triggers ClientState.Exception
                }
            }
    }

    private val _client: NetClient
    private val _state: ClientStateMachine

    private var _socket:   NetSocket? = null
    private var _clientID: String     = ""

    init {
        _state = machine {

            definitions { CLIENT_STATES }

            ClientState.Connected state {

                onEnter {

                    this@machine trigger ClientAction.Ready

                    StateHandlerResult.Continue
                }
            }

            ClientState.Disconnected or ClientState.Exception state {

                onEnter {

                    close()

                    StateHandlerResult.Continue
                }
            }

            initiate()
        }

        _client = client {

            connect(port, host.hostAddress) {
                if (it.succeeded()) {
                    _socket = it.result()

                    _socket!! setup  {

                        closeHandler {

                            close()

                            ClientMessage.Output message "Connection $host:$port [$_clientID] Closed"

                            _state trigger ClientAction.Disconnect
                        }

                        exceptionHandler {
                            ex ->

                            onError(ex)

                            ClientMessage.Output message "Client $host:$port [$_clientID] failed: ${ex.message}"

                            _state trigger ClientAction.Exception
                        }

                        handler {
                            buffer ->

                            val data: String = buffer.toString()

                            if (data.contains("id", ignoreCase = true)) {
                                _clientID = data.split(":").filterIndexed { index, _ -> index > 0 }.lastOrNull() ?: "N/A"

                                ClientEvent.ID changed _clientID
                            }

                            ClientMessage.DataReceived message data
                        }
                    }
                } else {
                    ClientMessage.Output message "Failed to connect to $host:$port: ${it.cause().message}"

                    onError(it.cause())

                    _state trigger ClientAction.Exception
                }
            }
        }
    }

    override val events: ClientEvents by lazy { EventPublisher.create<Client, ClientEvent>() }

    override val state: ClientStates by lazy { _state }

    val clientID: String get() = _clientID

    fun send(data: String): Unit {

        _socket?.write(data)
    }

    override fun close() {

        _socket?.close()
        _client.close()
    }
}
