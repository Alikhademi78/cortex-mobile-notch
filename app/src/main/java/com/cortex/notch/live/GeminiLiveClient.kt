package com.cortex.notch.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.cortex.notch.data.CortexPreferences
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

enum class LiveState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

class GeminiLiveClient(private val context: Context) {

    val state = mutableStateOf(LiveState.IDLE)
    val audioAmplitude = mutableFloatStateOf(0f)
    val errorMessage = mutableStateOf<String?>(null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null
    private var isRecording = false
    private var recordJob: Job? = null
    private var playJob: Job? = null
    private var setupTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val audioPlayQueue = LinkedBlockingQueue<ByteArray>()

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Volatile
    private var isPlayingAudio = false

    @Volatile
    private var lastPlaybackFinishedTime = 0L

    fun isConnected(): Boolean {
        return state.value == LiveState.LISTENING ||
                state.value == LiveState.THINKING ||
                state.value == LiveState.SPEAKING
    }

    fun connect(apiKey: String) {
        if (apiKey.isBlank()) {
            scope.launch(Dispatchers.Main.immediate) {
                errorMessage.value = "لطفاً ابتدا کلید API جمنای را در تنظیمات برنامه وارد کنید."
                state.value = LiveState.ERROR
            }
            return
        }

        if (isConnected() || state.value == LiveState.CONNECTING) {
            return
        }

        disconnect(keepErrorState = true)
        scope.launch(Dispatchers.Main.immediate) {
            errorMessage.value = null
            state.value = LiveState.CONNECTING
        }

        val endpoint = CortexPreferences.OFFICIAL_LIVE_ENDPOINT
        val wsUrl = "$endpoint?key=$apiKey"
        Log.d("GeminiLiveClient", "Connecting directly to Gemini Live official endpoint")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        lateinit var currentSocket: WebSocket
        currentSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (ws !== webSocket) return
                Log.d("GeminiLiveClient", "WebSocket Connected! Sending Setup...")
                sendSetupMessage(ws)
                setupTimeoutJob?.cancel()
                setupTimeoutJob = scope.launch(Dispatchers.Main.immediate) {
                    delay(15000)
                    if (ws === webSocket && state.value == LiveState.CONNECTING) {
                        Log.w("GeminiLiveClient", "Setup timed out after 15s")
                        errorMessage.value = "اتصال برقرار نشد! مشکل از اینترنت است و باید یک VPN با آی‌پی خوب روشن کنید."
                        disconnect(keepErrorState = true)
                        state.value = LiveState.ERROR
                    }
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (ws !== webSocket) return
                Log.d("GeminiLiveClient", "WS Text: ${text.take(120)}")
                handleServerMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (ws !== webSocket) return
                val text = bytes.utf8()
                Log.d("GeminiLiveClient", "WS Binary: ${text.take(120)}")
                handleServerMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (ws !== webSocket) return
                setupTimeoutJob?.cancel()
                Log.e("GeminiLiveClient", "WebSocket Error: ${t.message}, responseCode=${response?.code}")

                val serverMsg = try { response?.body?.string() } catch (_: Exception) { null }
                val isHtmlError = serverMsg?.contains("<html", ignoreCase = true) == true ||
                        serverMsg?.contains("<!doctype", ignoreCase = true) == true

                val errDesc = when {
                    response?.code == 403 && isHtmlError ->
                        "مشکل از اینترنت است! اتصال به سرور گوگل مسدود شده؛ لطفاً یک VPN با آی‌پی خوب روشن کنید و دوباره امتحان کنید."
                    response?.code == 403 && serverMsg?.contains("API key", ignoreCase = true) == true ->
                        "کلید API معتبر نیست یا دسترسی ندارد. کلید را چک کنید و مطمئن شوید VPN با آی‌پی خوب روشن است."
                    response?.code == 403 ->
                        "مشکل از اینترنت یا محدودیت آی‌پی است (403). لطفاً VPN با آی‌پی خوب روشن کنید."
                    response?.code == 400 ->
                        "درخواست نامعتبر (400): لطفاً کلید API را در تنظیمات بررسی کنید."
                    response?.code == 429 ->
                        "سهمیه کلید API موقتاً پر شده است (429). کمی بعد دوباره تلاش کنید."
                    response != null && !serverMsg.isNullOrBlank() && !isHtmlError ->
                        "خطای سرور (${response.code}): ${serverMsg.take(120)}"
                    else ->
                        "مشکل از اینترنت است! اتصال برقرار نشد؛ لطفاً اینترنت و VPN با آی‌پی خوب را روشن کنید."
                }
                scope.launch(Dispatchers.Main.immediate) {
                    errorMessage.value = errDesc
                    disconnect(keepErrorState = true)
                    state.value = LiveState.ERROR
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (ws !== webSocket) return
                setupTimeoutJob?.cancel()
                Log.d("GeminiLiveClient", "WebSocket Closed: $code - $reason")

                scope.launch(Dispatchers.Main.immediate) {
                    if (code != 1000) {
                        errorMessage.value = when {
                            code == 1007 || reason.contains("location", ignoreCase = true) || reason.contains("region", ignoreCase = true) ->
                                "مشکل از اینترنت و محدودیت منطقه است! لطفاً VPN با آی‌پی خوب روشن کنید."
                            reason.contains("API key", ignoreCase = true) ->
                                "کلید API نامعتبر است؛ لطفاً کلید خود را در تنظیمات بررسی کنید."
                            else ->
                                "ارتباط قطع شد ($code). مشکل از اینترنت است؛ لطفاً VPN با آی‌پی خوب روشن کنید."
                        }
                        disconnect(keepErrorState = true)
                        state.value = LiveState.ERROR
                    } else {
                        disconnect()
                    }
                }
            }
        })
        webSocket = currentSocket
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val systemPrompt = CortexPreferences.getSystemPrompt(context)
            val voiceName = CortexPreferences.getVoiceOption(context).voiceName
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/gemini-3.8-live")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemPrompt)
                            })
                        })
                    })
                })
            }
            ws.send(setupJson.toString())
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Failed to send setup", e)
        }
    }

    private fun handleServerMessage(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)

            if (root.has("error")) {
                val errObj = root.optJSONObject("error")
                val errMsg = errObj?.optString("message") ?: root.optString("error")
                val errCode = errObj?.optInt("code") ?: 0
                val formattedErr = when {
                    errMsg.contains("location", ignoreCase = true) || errMsg.contains("region", ignoreCase = true) ->
                        "مشکل از اینترنت و محدودیت منطقه است! لطفاً VPN با آی‌پی خوب روشن کنید."
                    errMsg.contains("API key", ignoreCase = true) ->
                        "کلید API نامعتبر است؛ لطفاً کلید را در تنظیمات بررسی کنید."
                    errCode != 0 -> "خطای سرویس ($errCode): $errMsg"
                    else -> "خطای سرویس: $errMsg"
                }
                Log.e("GeminiLiveClient", "Server returned error: $formattedErr")
                scope.launch(Dispatchers.Main.immediate) {
                    errorMessage.value = formattedErr
                    disconnect(keepErrorState = true)
                    state.value = LiveState.ERROR
                }
                return
            }

            if (root.has("setupComplete")) {
                Log.d("GeminiLiveClient", "Setup Complete! Listening for user voice...")
                setupTimeoutJob?.cancel()
                setupTimeoutJob = null
                scope.launch(Dispatchers.Main.immediate) {
                    state.value = LiveState.LISTENING
                }
                startAudioPlayback()
                startAudioRecording()
                return
            }

            val serverContent = root.optJSONObject("serverContent") ?: return

            if (serverContent.optBoolean("interrupted", false)) {
                Log.d("GeminiLiveClient", "Gemini Interrupted!")
                audioPlayQueue.clear()
                isPlayingAudio = false
                lastPlaybackFinishedTime = System.currentTimeMillis()
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.play()
                scope.launch(Dispatchers.Main.immediate) {
                    state.value = LiveState.LISTENING
                }
                return
            }

            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null) {
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        val inlineData = part.optJSONObject("inlineData") ?: continue
                        val mimeType = inlineData.optString("mimeType", "")
                        if (mimeType.startsWith("audio/pcm")) {
                            val base64Data = inlineData.optString("data", "")
                            if (base64Data.isNotBlank()) {
                                val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                audioPlayQueue.offer(pcmBytes)
                                scope.launch(Dispatchers.Main.immediate) {
                                    if (state.value != LiveState.SPEAKING) {
                                        state.value = LiveState.SPEAKING
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (serverContent.optBoolean("turnComplete", false)) {
                scope.launch {
                    while (!audioPlayQueue.isEmpty()) {
                        delay(60)
                    }
                    delay(300)
                    isPlayingAudio = false
                    lastPlaybackFinishedTime = System.currentTimeMillis()
                    withContext(Dispatchers.Main.immediate) {
                        if (state.value == LiveState.SPEAKING) {
                            state.value = LiveState.LISTENING
                            audioAmplitude.floatValue = 0f
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Error parsing server message", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        if (isRecording) return
        isRecording = true

        recordJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("GeminiLiveClient", "AudioRecord initialization failed, state=${audioRecord?.state}")
                    isRecording = false
                    return@launch
                }

                val sessionId = audioRecord?.audioSessionId ?: 0
                if (sessionId != 0) {
                    if (AcousticEchoCanceler.isAvailable()) {
                        try {
                            echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                                enabled = true
                            }
                            Log.d("GeminiLiveClient", "AcousticEchoCanceler enabled: ${echoCanceler?.enabled}")
                        } catch (e: Exception) {
                            Log.w("GeminiLiveClient", "Failed to setup AcousticEchoCanceler", e)
                        }
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        try {
                            noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                                enabled = true
                            }
                            Log.d("GeminiLiveClient", "NoiseSuppressor enabled: ${noiseSuppressor?.enabled}")
                        } catch (e: Exception) {
                            Log.w("GeminiLiveClient", "Failed to setup NoiseSuppressor", e)
                        }
                    }
                }

                audioRecord?.startRecording()
                val buffer = ByteArray(1600)

                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read < 0) {
                        Log.w("GeminiLiveClient", "AudioRecord read returned error code: $read")
                        delay(100)
                        continue
                    }
                    if (read > 0) {
                        // Half-duplex echo gate: if model is speaking or within 400ms echo tail, drop mic chunks
                        val isModelSpeaking = isPlayingAudio || state.value == LiveState.SPEAKING
                        val timeSincePlayback = System.currentTimeMillis() - lastPlaybackFinishedTime

                        if (isModelSpeaking || timeSincePlayback < 400) {
                            continue
                        }

                        val rms = calculateRms(buffer, read)
                        val normAmp = (rms / 2200f).coerceIn(0f, 1f)

                        withContext(Dispatchers.Main.immediate) {
                            if (state.value == LiveState.LISTENING) {
                                audioAmplitude.floatValue = normAmp
                            }
                        }

                        val base64Chunk = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        val realtimeInput = JSONObject().apply {
                            put("realtimeInput", JSONObject().apply {
                                put("audio", JSONObject().apply {
                                    put("mimeType", "audio/pcm;rate=16000")
                                    put("data", base64Chunk)
                                })
                            })
                        }
                        webSocket?.send(realtimeInput.toString())
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("GeminiLiveClient", "Audio recording error", e)
                }
            } finally {
                isRecording = false
                try {
                    echoCanceler?.release()
                } catch (_: Exception) {}
                echoCanceler = null

                try {
                    noiseSuppressor?.release()
                } catch (_: Exception) {}
                noiseSuppressor = null

                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {
                    // Ignore
                }
                audioRecord = null
            }
        }
    }

    private fun startAudioPlayback() {
        playJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes((minBufferSize * 2).coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                while (isActive) {
                    val chunk = audioPlayQueue.poll(150, TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        isPlayingAudio = true
                        val rms = calculateRms(chunk, chunk.size)
                        val normAmp = (rms / 2800f).coerceIn(0f, 1f)

                        withContext(Dispatchers.Main.immediate) {
                            if (state.value == LiveState.SPEAKING) {
                                audioAmplitude.floatValue = normAmp
                            }
                        }

                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        if (isPlayingAudio) {
                            isPlayingAudio = false
                            lastPlaybackFinishedTime = System.currentTimeMillis()
                        }
                        withContext(Dispatchers.Main.immediate) {
                            if (state.value == LiveState.SPEAKING && audioPlayQueue.isEmpty()) {
                                state.value = LiveState.LISTENING
                                audioAmplitude.floatValue = 0f
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("GeminiLiveClient", "Audio playback error", e)
                }
            } finally {
                isPlayingAudio = false
                lastPlaybackFinishedTime = System.currentTimeMillis()
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {
                    // Ignore
                }
                audioTrack = null
            }
        }
    }

    fun disconnect(keepErrorState: Boolean = false) {
        isRecording = false
        isPlayingAudio = false
        lastPlaybackFinishedTime = 0L

        recordJob?.cancel()
        recordJob = null
        playJob?.cancel()
        playJob = null
        setupTimeoutJob?.cancel()
        setupTimeoutJob = null

        audioPlayQueue.clear()

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
            // Ignore
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
            // Ignore
        }
        audioTrack = null

        val oldSocket = webSocket
        webSocket = null
        try {
            oldSocket?.close(1000, "User disconnect")
        } catch (_: Exception) {
            // Ignore
        }

        scope.launch(Dispatchers.Main.immediate) {
            if (!keepErrorState) {
                state.value = LiveState.IDLE
            }
            audioAmplitude.floatValue = 0f
        }
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += (sample * sample).toDouble()
            count++
            i += 2
        }
        return if (count > 0) sqrt(sum / count).toFloat() else 0f
    }
}
