package com.giva.hiassist.stt

import com.giva.hiassist.BuildConfig
import com.giva.hiassist.domain.Caption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Real-time BHASHINI streaming STT adapter.
 *
 * Protocol based on the current BHASHINI microphone streaming example:
 *   wss://tts.bhashini.ai/stt/stream
 *   WebSocket subprotocol: apikey.<API_KEY>
 *   16 kHz / mono / signed PCM16 LE
 *
 * The key remains outside source control as a BuildConfig property.
 */
class BhashiniSttEngine : SttEngine {
    private val output = MutableSharedFlow<Caption>(extraBufferCapacity = 32)
    override val results: Flow<Caption> = output

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = false
    private var language = "English"

    override suspend fun start(language: String) {
        this.language = languageName(language)
        if (!configured()) {
            output.tryEmit(Caption(text = "BHASHINI API key is not configured. Use the on-device engine, or rebuild with BHASHINI_API_KEY.", isFinal = true))
            return
        }
        if (socket != null) return

        val url = BuildConfig.BHASHINI_BASE_URL.ifBlank { DEFAULT_WS_URL }
        val protocol = "apikey.${BuildConfig.BHASHINI_API_KEY}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Sec-WebSocket-Protocol", protocol)
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ready = true
                webSocket.send(startMessage(this@BhashiniSttEngine.language).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseServerMessage(text)?.let { output.tryEmit(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                ready = false
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false
                socket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false
                socket = null
                output.tryEmit(Caption(text = "BHASHINI connection error: ${t.message ?: "unknown error"}", isFinal = true))
            }
        })
    }

    override suspend fun acceptPcm16(samples: ShortArray) {
        val ws = socket ?: return
        if (!ready || samples.isEmpty()) return
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(data::putShort)
        ws.send(data.array().toByteString())
    }

    override suspend fun stop() {
        socket?.let { ws ->
            if (ready) ws.send(JSONObject().put("event", "finalize").toString())
            ws.close(1000, "session complete")
        }
        ready = false
        socket = null
    }

    private fun configured(): Boolean = BuildConfig.BHASHINI_API_KEY.isNotBlank()

    private fun startMessage(language: String) = JSONObject().apply {
        put("event", "start")
        put("language", language)
        put("useVad", true)
        put("inputEncoding", JSONObject().apply {
            put("encoding", "linear16")
            put("samplingRate", 16000)
            put("bitsPerSample", 16)
            put("numChannels", 1)
            put("isSigned", true)
            put("isBigEndian", false)
        })
        put("vadConfig", JSONObject().apply {
            put("pStart", 0.6)
            put("pauseMs", 400)
            put("endMs", 1200)
        })
        put("interimIntervalMs", 200)
    }

    private fun parseServerMessage(raw: String): Caption? = runCatching {
        val root = JSONObject(raw)
        val event = root.optString("event")
        val text = sequenceOf(
            root.optString("text"),
            root.optString("transcript"),
            root.optJSONObject("result")?.optString("text").orEmpty(),
            root.optJSONObject("result")?.optString("transcript").orEmpty(),
            root.optJSONObject("data")?.optString("text").orEmpty(),
            root.optJSONObject("data")?.optString("transcript").orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        if (text.isBlank()) return@runCatching null
        val explicitFinal = when {
            root.has("isFinal") -> root.optBoolean("isFinal")
            root.has("is_final") -> root.optBoolean("is_final")
            root.optJSONObject("result")?.has("isFinal") == true -> root.optJSONObject("result")!!.optBoolean("isFinal")
            else -> event.equals("final", true) || event.equals("final_result", true)
        }
        Caption(text = text, isFinal = explicitFinal)
    }.getOrNull()

    private fun languageName(codeOrName: String): String = when (codeOrName.lowercase()) {
        "en", "english" -> "English"
        "hi", "hindi" -> "Hindi"
        "bn", "bengali" -> "Bengali"
        "kn", "kannada" -> "Kannada"
        "ta", "tamil" -> "Tamil"
        "te", "telugu" -> "Telugu"
        "ml", "malayalam" -> "Malayalam"
        "mr", "marathi" -> "Marathi"
        "gu", "gujarati" -> "Gujarati"
        "pa", "punjabi" -> "Punjabi"
        "or", "odia", "oriya" -> "Odia"
        else -> codeOrName
    }

    companion object {
        private const val DEFAULT_WS_URL = "wss://tts.bhashini.ai/stt/stream"
    }
}
