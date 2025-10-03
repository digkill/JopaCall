package org.mediarise.dialer.signaling

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SignalingClient(
    private val baseWs: String,
    parentScope: CoroutineScope,
    private val listener: Listener
) {
    interface Listener {
        fun onPeerJoined(peerId: String)
        fun onOffer(from: String, sdp: String)
        fun onAnswer(from: String, sdp: String)
        fun onIce(from: String, mid: String, index: Int, cand: String)
        fun onClosed(reason: String)
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var currentRoomId: String? = null
    val id: String = UUID.randomUUID().toString()

    private val isConnecting = AtomicBoolean(false)
    // --- ГЛАВНОЕ ИСПРАВЛЕНИЕ: Заменено 'nil' на 'null' ---
    private var reconnectionJob: Job? = null

    fun connect(roomId: String) {
        if (!isConnecting.compareAndSet(false, true)) {
            Log.w("SignalingClient", "Connection attempt already in progress for room: $roomId")
            return
        }

        // Если уже есть активный сокет, закрываем его перед созданием нового.
        webSocket?.close(1000, "Changing room")
        webSocket = null

        this.currentRoomId = roomId

        // Используем URL с query-параметрами для решения проблемы '404 Not Found'.
        val url = "$baseWs?room=$roomId&peer=$id"
        val request = Request.Builder().url(url).build()

        Log.d("SignalingClient", "Connecting to URL: $url")
        client.newWebSocket(request, SignalingWebSocketListener())
    }

    private inner class SignalingWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            this@SignalingClient.webSocket = webSocket
            isConnecting.set(false)
            reconnectionJob?.cancel() // Отменяем любые запланированные переподключения
            Log.i("SignalingClient", "WebSocket connection opened to room: $currentRoomId")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                val envelope = runCatching { json.decodeFromString<Envelope>(text) }.getOrNull() ?: return@launch
                if (envelope.from == id) return@launch // Игнорируем свои же сообщения

                Log.d("SignalingClient", "Received message: ${envelope.type} from ${envelope.from}")
                when (envelope.type) {
                    "peer-joined" -> envelope.peerId?.let { listener.onPeerJoined(it) }
                    "offer" -> envelope.sdp?.let { listener.onOffer(envelope.from!!, it) }
                    "answer" -> envelope.sdp?.let { listener.onAnswer(envelope.from!!, it) }
                    "ice" -> {
                        val c = envelope.candidate ?: return@launch
                        listener.onIce(envelope.from!!, c.sdpMid!!, c.sdpMLineIndex!!, c.candidate!!)
                    }
                    "error" -> Log.e("SignalingClient", "Server error: ${envelope.payload}")
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w("SignalingClient", "WebSocket closing: $code / $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@SignalingClient.webSocket = null
            isConnecting.set(false)
            Log.w("SignalingClient", "WebSocket closed: $code / $reason")
            listener.onClosed("Connection closed: $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            this@SignalingClient.webSocket = null
            isConnecting.set(false)
            Log.e("SignalingClient", "WebSocket failure", t)
            // Запускаем переподключение только если это не было сделано ранее
            if (reconnectionJob?.isActive != true) {
                reconnect()
            }
        }
    }

    private fun reconnect() {
        val roomToReconnect = currentRoomId ?: return
        reconnectionJob = scope.launch {
            Log.d("SignalingClient", "Scheduling reconnect in 3 seconds...")
            delay(3000)
            if (webSocket == null) {
                connect(roomToReconnect)
            }
        }
    }

    fun sendOffer(sdp: String) = send(Envelope(type = "offer", sdp = sdp))
    fun sendAnswer(sdp: String) = send(Envelope(type = "answer", sdp = sdp))
    fun sendIce(mid: String, idx: Int, cand: String) {
        send(Envelope(type = "ice", candidate = Candidate(mid, idx, cand)))
    }

    fun leave() {
        scope.coroutineContext[Job]?.cancel() // Отменяем все корутины, включая reconnect
        webSocket?.close(1000, "User left")
        webSocket = null
        currentRoomId = null
    }

    private fun send(data: Envelope) {
        if (webSocket == null) {
            Log.w("SignalingClient", "Cannot send message, WebSocket is not active.")
            return
        }
        scope.launch {
            // Дополняем сообщение данными о комнате и отправителе
            val messageToSend = data.copy(room = currentRoomId, from = id)
            val message = json.encodeToString(messageToSend)
            webSocket?.send(message)
        }
    }
}

@Serializable
data class Envelope(
    val type: String,
    val room: String? = null,
    val peerId: String? = null,
    val from: String? = null,
    val sdp: String? = null,
    val candidate: Candidate? = null,
    val payload: String? = null // Поле для доп. информации, например, текста ошибки
)

@Serializable
data class Candidate(
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val candidate: String? = null
)
