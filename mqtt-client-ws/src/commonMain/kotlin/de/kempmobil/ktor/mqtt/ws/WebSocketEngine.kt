package de.kempmobil.ktor.mqtt.ws

import de.kempmobil.ktor.mqtt.ConnectionException
import de.kempmobil.ktor.mqtt.MalformedPacketException
import de.kempmobil.ktor.mqtt.MqttEngine
import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.readPacket
import de.kempmobil.ktor.mqtt.packet.write
import de.kempmobil.ktor.mqtt.util.Logger
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.io.Buffer

internal class WebSocketEngine(private val config: WebSocketEngineConfig) : MqttEngine {

    private val client: HttpClient = config.http()

    private val _packetResults = MutableSharedFlow<Result<Packet>>(1)
    override val packetResults: SharedFlow<Result<Packet>>
        get() = _packetResults

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean>
        get() = _connected

    private val scope = CoroutineScope(config.dispatcher)

    private var receiverJob: Job? = null
    private var wsSession: DefaultClientWebSocketSession? = null

    init {
        if (client.pluginOrNull(WebSockets) == null) {
            throw IllegalStateException("No WebSockets plugin installed in ${client.engine::class.simpleName}, consider using 'install(WebSockets)'")
        }
    }

    override suspend fun start(): Result<Unit> {
        return try {
            if (!config.url.user.isNullOrBlank() || !config.url.password.isNullOrBlank()) {
                Logger.w { "Username/password encoded in URL cannot be used in websocket connections" }
            }
            wsSession = client.webSocketSession(
                method = HttpMethod.Get,
                host = config.url.host,
                port = config.url.port,
                path = config.url.encodedPath
            ) {
                url.protocol = when (val protocol = config.url.protocol) {
                    URLProtocol.WS, URLProtocol.HTTP -> URLProtocol.WS
                    URLProtocol.WSS, URLProtocol.HTTPS -> URLProtocol.WSS
                    else -> {
                        throw IllegalArgumentException("Unexpected web socket protocol: $protocol (use http(s) or ws(s))")
                    }
                }
                headers[HttpHeaders.SecWebSocketProtocol] = "mqtt"
            }.also {
                _connected.emit(true)
                receiverJob = scope.launch {
                    it.incomingMessagesLoop()
                }
            }
            Result.success(Unit)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Result.failure(ConnectionException("Cannot connect to ${config.url}", ex))
        }
    }

    override suspend fun send(packet: Packet): Result<Unit> {
        return wsSession?.doSend(packet)
            ?: Result.failure(ConnectionException("Not connected to ${config.url}"))
    }

    override suspend fun disconnect() {
        wsSession?.let {
            wsSession = null
            it.close()
        }
        receiverJob?.cancel()
    }

    override fun close() {
        client.close()
        scope.cancel()
    }

    override fun toString(): String {
        return "WebSocketMqttEngine[${config.url}]"
    }

    private suspend fun DefaultClientWebSocketSession.incomingMessagesLoop() {
        // As we cannot assume that MQTT Control Packets are aligned on WebSocket frame boundaries. Hence, use a
        // ByteChannel where we write all received binary frames to and read the packets from it, as soon as a complete
        // packet is available. This works because unlike Buffer.read(), Channel.read() can suspend until new bytes are
        // available.
        val channel = ByteChannel(autoFlush = true)
        val reader = launch {
            while (!channel.isClosedForRead) {
                _packetResults.emit(Result.success(channel.readPacket()))
            }
        }

        try {
            while (receiverJob?.isActive == true) {
                try {
                    for (frame in incoming) {
                        when (frame) {
                            // Note that in non-raw mode, we should never receive Close, Ping or Pong frames
                            is Frame.Binary -> {
                                Logger.v { "Received data frame of size: ${frame.data.size}" }
                                channel.writeFully(frame.readBytes())
                            }

                            else -> {
                                // TODO: Close the network connection when receiving a non-binary frame [MQTT-6.0.0-1]
                                Logger.e { "Received unexpected frame type: $frame" }
                            }
                        }
                    }
                    Logger.d { "Incoming message loop terminated (no more web socket frames available)" }

                    // When we come here, the connection has been terminated, hence do some cleanup
                    disconnect()
                } catch (ex: CancellationException) {
                    Logger.v { "Incoming message queue of ${this@WebSocketEngine} has been cancelled" }
                    disconnect()
                    throw ex
                } catch (ex: MalformedPacketException) {
                    // Continue with the loop, so that the client can decide what to do
                    _packetResults.emit(Result.failure(ex))
                } catch (ex: Exception) {
                    Logger.e(throwable = ex) { "Error while receiving messages: " + ex::class }
                }
            }
        } finally {
            reader.cancel()
            _connected.emit(false)
        }
    }

    private suspend fun DefaultClientWebSocketSession.doSend(packet: Packet): Result<Unit> {
        Logger.d { "Sending $packet..." }

        return try {
            Buffer().use { buffer ->
                buffer.write(packet)

                if (buffer.size <= maxFrameSize) {
                    outgoing.send(Frame.Binary(fin = true, packet = buffer))
                } else {
                    Buffer().use { frame ->
                        while (buffer.size > 0) {
                            buffer.readAtMostTo(frame, buffer.size.coerceAtMost(maxFrameSize))
                            outgoing.send(Frame.Binary(fin = true, packet = frame))
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.w(throwable = ex) { "Write socket error detected" }
            Result.failure(ex)
        }
    }
}
