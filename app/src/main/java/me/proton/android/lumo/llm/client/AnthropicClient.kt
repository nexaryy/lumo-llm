package me.proton.android.lumo.llm.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.proton.android.lumo.llm.model.AnthropicStreamEvent
import me.proton.android.lumo.llm.model.ChatMessage
import me.proton.android.lumo.llm.model.ResolvedModelConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Anthropic Messages API client (Claude family).
 *
 * Docs: https://docs.anthropic.com/en/api/messages-streaming
 *
 * Anthropic uses:
 *   - `x-api-key: <key>`  (NOT Bearer)
 *   - `anthropic-version: 2023-06-01`
 *   - System prompt goes in a top-level `system` field (NOT in the messages array).
 */
class AnthropicClient(
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
        val payload = buildPayload(messages, config)
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = buildRequest(config, requestBody)

        Timber.tag(TAG).i("Anthropic → ${config.baseUrl}/v1/messages model=${config.model}")

        val factory = EventSources.createFactory(httpClient)
        val fullText = StringBuilder()

        val source = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                runCatching { json.decodeFromString<AnthropicStreamEvent>(data) }
                    .onSuccess { ev ->
                        when (ev.type) {
                            "message_stop" -> {
                                val elapsed = System.currentTimeMillis() - started
                                trySend(StreamEvent.Done(fullText.toString(), elapsed))
                                eventSource.cancel()
                                channel.close()
                            }
                            "content_block_delta" -> {
                                ev.delta?.text?.let { text ->
                                    if (text.isNotEmpty()) {
                                        fullText.append(text)
                                        trySend(StreamEvent.Delta(text))
                                    }
                                }
                            }
                            else -> {
                                ev.error?.let {
                                    trySend(StreamEvent.Error("Anthropic error: $it"))
                                }
                            }
                        }
                    }
                    .onFailure { Timber.tag(TAG).w("Failed to parse Anthropic event: $data — ${it.message}") }
            }

            override fun onClosed(eventSource: EventSource) {
                val elapsed = System.currentTimeMillis() - started
                if (!channel.isClosedForSend) {
                    trySend(StreamEvent.Done(fullText.toString(), elapsed))
                    channel.close()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val bodyText = runCatching { response?.body?.string().orEmpty() }.getOrDefault("")
                Timber.tag(TAG).e(t, "Anthropic stream failure. status=${response?.code} body=$bodyText")
                val msg = if (bodyText.isNotBlank()) "HTTP ${response?.code ?: 0}: $bodyText"
                else (t?.message ?: "Unknown network error")
                trySend(StreamEvent.Error(msg, t))
                channel.close()
            }
        })

        awaitClose {
            Timber.tag(TAG).d("Collector cancelled — cancelling Anthropic SSE source")
            source.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPayload(
        messages: List<ChatMessage>,
        config: ResolvedModelConfig,
    ): String {
        // Anthropic: system prompt is top-level, not in messages array.
        val (systemMessages, chatMessages) = messages.partition { it.role == "system" }
        val systemText = buildString {
            config.systemPrompt.takeIf { it.isNotBlank() }?.let { append(it); append("\n\n") }
            systemMessages.forEach { append(it.content); append("\n") }
        }.trim()

        val messagesArray = buildJsonArray {
            chatMessages.forEach { m ->
                add(buildJsonObject { put("role", m.role); put("content", m.content) })
            }
        }
        val obj = buildJsonObject {
            put("model", config.model)
            put("messages", messagesArray)
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature)
            put("stream", true)
            if (systemText.isNotBlank()) put("system", systemText)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun buildRequest(
        config: ResolvedModelConfig,
        body: okhttp3.RequestBody,
    ): Request {
        val url = config.baseUrl.trimEnd('/') + "/v1/messages"
        val builder = Request.Builder().url(url).post(body)
        if (config.apiKey.isNotBlank()) {
            builder.header("x-api-key", config.apiKey)
        }
        builder.header("anthropic-version", ANTHROPIC_VERSION)
        builder.header("Accept", "text/event-stream")
        config.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    companion object {
        private const val TAG = "AnthropicClient"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun buildHttpClient(config: ResolvedModelConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(config.requestTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(config.requestTimeoutSeconds * 2, TimeUnit.SECONDS)
                .build()
    }
}
