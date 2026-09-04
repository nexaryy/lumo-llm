package me.proton.android.lumo.llm.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.proton.android.lumo.llm.model.ChatMessage
import me.proton.android.lumo.llm.model.OpenAiChunk
import me.proton.android.lumo.llm.model.ResolvedModelConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import okio.IOException
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Works with OpenAI, OpenRouter, Together, Groq, Mistral, DeepSeek, xAI, Ollama (with `/v1`),
 * LM Studio, vLLM, etc. — anything that speaks the OpenAI Chat Completions API.
 *
 * Supports both `stream: true` (Server-Sent Events) and `stream: false` (single JSON response).
 */
class OpenAiCompatibleClient(
    private val httpClient: OkHttpClient,
) : LlmClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    override fun stream(
        messages: List<ChatMessage>,
        config: ResolvedModelConfig,
    ): Flow<StreamEvent> = callbackFlow {
        val started = System.currentTimeMillis()
        val payload = buildPayload(messages, config, stream = true)
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest(config, requestBody)

        Timber.tag(TAG).i("OpenAI-compat → ${config.baseUrl}/chat/completions model=${config.model}")

        val factory = EventSources.createFactory(httpClient)
        val fullText = StringBuilder()

        val source = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    val elapsed = System.currentTimeMillis() - started
                    trySend(StreamEvent.Done(fullText.toString(), elapsed))
                    eventSource.cancel()
                    channel.close()
                    return
                }
                runCatching { json.decodeFromString<OpenAiChunk>(data) }
                    .onSuccess { chunk ->
                        chunk.choices.firstOrNull()?.delta?.content?.let { delta ->
                            if (delta.isNotEmpty()) {
                                fullText.append(delta)
                                trySend(StreamEvent.Delta(delta))
                            }
                        }
                    }
                    .onFailure { Timber.tag(TAG).w("Failed to parse SSE chunk: $data — ${it.message}") }
            }

            override fun onClosed(eventSource: EventSource) {
                val elapsed = System.currentTimeMillis() - started
                if (!channel.isClosedForSend) {
                    trySend(StreamEvent.Done(fullText.toString(), elapsed))
                    channel.close()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val bodyText = runCatching {
                    response?.body?.string().orEmpty()
                }.getOrDefault("")
                Timber.tag(TAG).e(t, "OpenAI stream failure. status=${response?.code} body=$bodyText")
                val msg = if (bodyText.isNotBlank()) "HTTP ${response?.code ?: 0}: $bodyText"
                else (t?.message ?: "Unknown network error")
                trySend(StreamEvent.Error(msg, t))
                channel.close()
            }
        })

        awaitClose {
            Timber.tag(TAG).d("Collector cancelled — cancelling SSE source")
            source.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPayload(
        messages: List<ChatMessage>,
        config: ResolvedModelConfig,
        stream: Boolean,
    ): String {
        val messagesArray = buildJsonArray {
            messages.forEach { m ->
                add(buildJsonObject { put("role", m.role); put("content", m.content) })
            }
        }
        val obj = buildJsonObject {
            put("model", config.model)
            put("messages", messagesArray)
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)
            put("stream", stream)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun buildRequest(
        config: ResolvedModelConfig,
        body: okhttp3.RequestBody,
    ): Request {
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        val builder = Request.Builder().url(url).post(body)
        if (config.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.apiKey}")
        }
        builder.header("Accept", "text/event-stream")
        config.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    companion object {
        private const val TAG = "OpenAiCompat"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun buildHttpClient(config: ResolvedModelConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(config.requestTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(config.requestTimeoutSeconds * 2, TimeUnit.SECONDS)
                .build()
    }
}
