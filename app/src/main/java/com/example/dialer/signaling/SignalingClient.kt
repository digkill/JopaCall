// app/src/main/java/com/example/dialer/signaling/SignalingClient.kt — fix imports/serialization usage
package com.example.dialer.signaling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.ByteString
import java.util.UUID
import java.util.concurrent.TimeUnit

class SignalingClient(
    private val baseWs: String,
    private val scope: CoroutineScope,
    private val listener: Listener
) {
    interface Listener {
        fun onPeerJoined(peerId: String) {}
        fun onOffer(from: String, sdp: String) {}
        fun onAnswer(from: String, sdp: String) {}
        fun onIce(from: String, mid: String, index: Int, cand: String) {}
        fun onClosed() {}
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var room: String? = null
    val id: String = UUID.randomUUID().toString()

    fun connect(roomId: String) {
        room = roomId
        val req = Request.Builder().url(baseWs).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                send(Envelope(type = "join", room = roomId, peerId = id))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    val env = runCatching { json.decodeFromString<Envelope>(text) }.getOrNull() ?: return@launch
                    when (env.type) {
                        "peer-joined" -> env.peerId?.let { listener.onPeerJoined(it) }
                        "offer" -> listener.onOffer(env.from ?: return@launch, env.sdp ?: return@launch)
                        "answer" -> listener.onAnswer(env.from ?: return@launch, env.sdp ?: return@launch)
                        "ice" -> {
                            val c = env.candidate ?: return@launch
                            listener.onIce(env.from ?: return@launch, c.sdpMid ?: return@launch, c.sdpMLineIndex ?: return@launch, c.candidate ?: return@launch)
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { listener.onClosed() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onClosed()
                reconnect()
            }
        })
    }

    private fun reconnect() {
        val r = room ?: return
        scope.launch {
            delay(1200)
            connect(r)
        }
    }

    fun sendOffer(sdp: String) = send(Envelope(type = "offer", room = room, sdp = sdp))
    fun sendAnswer(sdp: String) = send(Envelope(type = "answer", room = room, sdp = sdp))
    fun sendIce(mid: String, idx: Int, cand: String) =
        send(Envelope(type = "ice", room = room, candidate = Candidate(mid, idx, cand)))

    fun leave() { send(Envelope(type = "leave", room = room)); ws?.close(1000, "bye") }

    private fun send(e: Envelope) {
        ws?.send(json.encodeToString(e))
    }
}

@Serializable
data class Envelope(
    val type: String,
    val room: String? = null,
    val peerId: String? = null,
    val from: String? = null,
    val sdp: String? = null,
    val candidate: Candidate? = null
)

@Serializable
data class Candidate(
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val candidate: String? = null
)
