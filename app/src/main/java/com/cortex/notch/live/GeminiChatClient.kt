package com.cortex.notch.live

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.cortex.notch.data.CortexPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false
)

class GeminiChatClient(private val context: Context) {

    val messages = mutableStateListOf<ChatMessage>()
    val isLoading = mutableStateOf(false)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun sendMessage(rawText: String, apiKey: String) {
        val text = rawText.trim()
        if (text.isBlank() || isLoading.value) return

        if (apiKey.isBlank()) {
            messages.add(
                ChatMessage(
                    isUser = false,
                    text = "ابتدا کلید رایگان Gemini API را در تنظیمات برنامه وارد کنید.",
                    isError = true
                )
            )
            return
        }

        messages.add(ChatMessage(isUser = true, text = text))
        isLoading.value = true

        // Snapshot conversation history (excluding error bubbles)
        val historySnapshot = messages.filter { !it.isError }.takeLast(14)
        val selectedModelId = CortexPreferences.getChatModel(context).modelId
        val modelOrder = listOf(
            selectedModelId,
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-2.5-flash"
        ).distinct()

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val systemPrompt = buildString {
                    append(CortexPreferences.getSystemPrompt(context))
                    append("\n\nدر چت متنی، پاسخ‌ها را خوانا، مرتب، دقیق و به زبان فارسی بنویس.")
                }

                val contentsArray = JSONArray().apply {
                    historySnapshot.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", if (msg.isUser) "user" else "model")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", msg.text)
                                })
                            })
                        })
                    }
                }

                val bodyJson = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemPrompt)
                            })
                        })
                    })
                    put("contents", contentsArray)
                }

                val result = executeWithFallback(bodyJson.toString(), apiKey, modelOrder)

                withContext(Dispatchers.Main.immediate) {
                    isLoading.value = false
                    result.onSuccess { reply ->
                        messages.add(ChatMessage(isUser = false, text = reply))
                    }.onFailure { err ->
                        messages.add(
                            ChatMessage(
                                isUser = false,
                                text = err.message ?: "خطا در برقراری ارتباط با سرور.",
                                isError = true
                            )
                        )
                    }
                }
            } catch (_: CancellationException) {
                withContext(Dispatchers.Main.immediate) {
                    isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("GeminiChatClient", "Unexpected chat error", e)
                withContext(Dispatchers.Main.immediate) {
                    isLoading.value = false
                    messages.add(
                        ChatMessage(
                            isUser = false,
                            text = "مشکل از اینترنت است! لطفاً VPN با آی‌پی خوب روشن کنید و دوباره تلاش کنید.",
                            isError = true
                        )
                    )
                }
            }
        }
    }

    private fun executeWithFallback(
        jsonPayload: String,
        apiKey: String,
        modelsToTry: List<String>
    ): Result<String> {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        var lastError: String? = null

        for (model in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody(mediaType))
                .build()

            var responseCode = 0
            var responseBody = ""
            var isSuccessful = false

            try {
                val response = httpClient.newCall(request).execute()
                try {
                    responseCode = response.code
                    isSuccessful = response.isSuccessful
                    responseBody = response.body?.string().orEmpty()
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                Log.e("GeminiChatClient", "Network call failed for $model: ${e.message}")
                lastError = "مشکل از اینترنت است! اتصال برقرار نشد؛ لطفاً اینترنت و VPN با آی‌پی خوب را روشن کنید."
                return Result.failure(IllegalStateException(lastError))
            }

            if (isSuccessful) {
                val replyText = extractReplyText(responseBody)
                if (!replyText.isNullOrBlank()) {
                    return Result.success(replyText)
                }
                lastError = "پاسخی از مدل دریافت نشد؛ لطفاً دوباره امتحان کنید."
                return Result.failure(IllegalStateException(lastError))
            }

            if (responseCode == 404) {
                Log.w("GeminiChatClient", "Model $model returned 404, trying fallback...")
                lastError = "مدل انتخابی در دسترس نبود (404)."
                continue
            }

            val isHtml = responseBody.contains("<html", ignoreCase = true) ||
                    responseBody.contains("<!doctype", ignoreCase = true)

            lastError = when {
                responseCode == 403 && isHtml ->
                    "مشکل از اینترنت است! اتصال به سرور گوگل مسدود شده؛ لطفاً VPN با آی‌پی خوب روشن کنید."
                responseCode == 403 && responseBody.contains("API key", ignoreCase = true) ->
                    "کلید API معتبر نیست یا دسترسی ندارد. کلید را در تنظیمات بررسی کنید."
                responseCode == 403 ->
                    "مشکل از اینترنت یا محدودیت آی‌پی است (403). لطفاً VPN با آی‌پی خوب روشن کنید."
                responseCode == 400 ->
                    "کلید API یا درخواست نامعتبر است (400). لطفاً کلید خود را بررسی کنید."
                responseCode == 429 ->
                    "سهمیه این مدل موقتاً پر شده است (429). کمی بعد یا با مدل سریع‌تر امتحان کنید."
                else ->
                    "خطای ارتباط با سرور ($responseCode). مطمئن شوید VPN با آی‌پی خوب روشن است."
            }
            return Result.failure(IllegalStateException(lastError))
        }

        return Result.failure(IllegalStateException(lastError ?: "خطا در دریافت پاسخ."))
    }

    private fun extractReplyText(rawJson: String): String? {
        return try {
            val root = JSONObject(rawJson)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val textPart = parts.optJSONObject(i)?.optString("text", "") ?: ""
                if (textPart.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(textPart)
                }
            }
            sb.toString().trim().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("GeminiChatClient", "Failed to parse generateContent JSON", e)
            null
        }
    }

    fun clearHistory() {
        activeJob?.cancel()
        activeJob = null
        isLoading.value = false
        messages.clear()
    }
}
