package me.proton.android.lumo.llm.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.proton.android.lumo.llm.model.ChatMessage
import me.proton.android.lumo.llm.model.ResolvedModelConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * "Bring your own endpoint" client.
 *
 * Use case: you have an LLM behind some weird HTTP API (a self-hosted gateway, a cloud function,
 * a private server with a custom schema, a Hugging Face TGI, etc.) — you don't want to write
 * a Kotlin adapter, you just want to send a request like:
 *
 *   POST https://my-server.example.com/ask
 *   Authorization: Bearer xyz
 *   X-Custom-Header: yes
 *   Content-Type: application/json
 *
 *   {
 *     "model": "my-model",
 *     "question": "{{prompt}}",
 *     "system": "{{system}}",
 *     "history": {{history}},
 *     "temperature": 0.7
 *   }
 *
 * And tell Lumo where to find the answer:
 *
 *   response.data.text
 *
 * Placeholders supported in the body template:
 *   {{prompt}}   → last user message text
 *   {{system}}   → system prompt text (JSON-escaped string)
 *   {{history}}  → JSON array of previous messages: [{"role":"user","content":"..."}, ...]
 *   {{model}}    → resolved model name
 *
 * If the body parses as JSON, placeholders are replaced as JSON values; otherwise as plain strings.
 *
 * The `responsePath` is a small subset of JSONPath:
 *   - `$.field`                       → field of root
 *   - `$.a.b.c`                       → nested field
 *   - `$.choices[0].message.content`  → array index + nested fields
 *   - `$.data[0].text`                → etc.
 *
 * Streaming is NOT supported — the response is awaited as a single JSON document and the full
 * text is emitted as one [StreamEvent.Delta] followed by [StreamEvent.Done].
 */
class CustomHttpClient(
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

        val promptText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val systemText = messages.firstOrNull { it.role == "system" }?.content
            ?: config.systemPrompt
        val historyArray = messages.filter { it.role != "system" }.dropLast(1)

        val renderedBody = renderBody(
            template = config.customBodyTemplate,
            prompt = promptText,
            system = systemText,
            history = historyArray,
            model = config.model,
        )

        val request = buildRequest(config, renderedBody)

        Timber.tag(TAG).i("Custom HTTP → ${config.baseUrl} (${config.customMethod})")

        try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    trySend(
                        StreamEvent.Error("HTTP ${response.code}: ${raw.take(2000)}")
                    )
                    channel.close()
                    return@use
                }

                val text = extractText(raw, config.customResponsePath)
                if (text == null) {
                    trySend(
                        StreamEvent.Error(
                            "Could not extract text at `${config.customResponsePath}`. Raw body: ${raw.take(2000)}"
                        )
                    )
                } else {
                    trySend(StreamEvent.Delta(text))
                    trySend(StreamEvent.Done(text, System.currentTimeMillis() - started))
                }
                channel.close()
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Custom HTTP failure")
            trySend(StreamEvent.Error(t.message ?: "Network error", t))
            channel.close()
        }

        awaitClose { /* synchronous call above; nothing to cancel */ }
    }.flowOn(Dispatchers.IO)

    private fun renderBody(
        template: String,
        prompt: String,
        system: String,
        history: List<ChatMessage>,
        model: String,
    ): String {
        if (template.isBlank()) {
            // Sensible default: OpenAI-compatible shape.
            val obj = buildJsonObject {
                put("model", model)
                put("messages", buildJsonArray {
                    if (system.isNotBlank()) {
                        add(buildJsonObject { put("role", "system"); put("content", system) })
                    }
                    history.forEach { m ->
                        add(buildJsonObject { put("role", m.role); put("content", m.content) })
                    }
                    add(buildJsonObject { put("role", "user"); put("content", prompt) })
                })
            }
            return json.encodeToString(JsonObject.serializer(), obj)
        }

        // If the template parses as JSON, do a proper JSON-aware replacement (strings stay
        // strings, arrays stay arrays, etc.). Otherwise, do a plain text replacement.
        val parsed = runCatching { json.parseToJsonElement(template) }.getOrNull()
        return if (parsed != null) {
            val substituted = substituteJson(parsed, prompt, system, history, model)
            json.encodeToString(JsonElement.serializer(), substituted)
        } else {
            template
                .replace("{{prompt}}", prompt)
                .replace("{{system}}", system)
                .replace("{{model}}", model)
                .replace("{{history}}", json.encodeToString(JsonArray.serializer(), buildJsonArray {
                    history.forEach { m ->
                        add(buildJsonObject { put("role", m.role); put("content", m.content) })
                    }
                }))
        }
    }

    private fun substituteJson(
        element: JsonElement,
        prompt: String,
        system: String,
        history: List<ChatMessage>,
        model: String,
    ): JsonElement = when (element) {
        is JsonPrimitive -> {
            val raw = element.contentOrNull() ?: return element
            val replaced = raw
                .replace("{{prompt}}", prompt)
                .replace("{{system}}", system)
                .replace("{{model}}", model)
            JsonPrimitive(replaced)
        }
        is JsonObject -> {
            val newObj = buildJsonObject {
                element.forEach { (k, v) -> put(k, substituteJson(v, prompt, system, history, model)) }
            }
            newObj
        }
        is JsonArray -> {
            buildJsonArray {
                element.forEach { add(substituteJson(it, prompt, system, history, model)) }
            }
        }
        else -> element
    }

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this.isString) this.content else (this.content.takeIf { it != "null" })

    private fun buildRequest(
        config: ResolvedModelConfig,
        body: String,
    ): Request {
        val builder = Request.Builder().url(config.baseUrl)
        val method = config.customMethod.uppercase().ifBlank { "POST" }
        val reqBody = body.toRequestBody(JSON_MEDIA_TYPE)

        when (method) {
            "POST" -> builder.post(reqBody)
            "PUT" -> builder.put(reqBody)
            "PATCH" -> builder.patch(reqBody)
            "DELETE" -> builder.delete(reqBody)
            "GET" -> builder.get()
            else -> builder.post(reqBody)
        }
        if (config.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.apiKey}")
        }
        config.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    /**
     * Extracts the answer text from the response body using a small JSONPath subset.
     * Supported: $.a.b.c, $.arr[0].field, $.arr[0][1].field, $  (returns the whole body as text).
     */
    fun extractText(body: String, path: String): String? {
        if (path.isBlank() || path == "$") return body
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return body
        var current: JsonElement? = element
        val tokens = parsePath(path)
        for (token in tokens) {
            current = when {
                current == null -> return null
                token is PathToken.Field && current is JsonObject -> current[token.name]
                token is PathToken.Index && current is JsonArray -> current.getOrNull(token.index)
                else -> return null
            }
            if (current == null) return null
        }
        return when (current) {
            is JsonPrimitive -> current.content
            is JsonElement -> json.encodeToString(JsonElement.serializer(), current)
            else -> null
        }
    }

    private sealed interface PathToken {
        data class Field(val name: String) : PathToken
        data class Index(val index: Int) : PathToken
    }

    private fun parsePath(path: String): List<PathToken> {
        val cleaned = path.removePrefix("\$").trim()
        if (cleaned.isEmpty()) return emptyList()
        val tokens = mutableListOf<PathToken>()
        val regex = Regex("""\.(\w+)|\[(\d+)\]""")
        regex.findAll(cleaned).forEach { m ->
            if (m.groupValues[1].isNotBlank()) {
                tokens.add(PathToken.Field(m.groupValues[1]))
            } else {
                tokens.add(PathToken.Index(m.groupValues[2].toInt()))
            }
        }
        return tokens
    }

    companion object {
        private const val TAG = "CustomHttp"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun buildHttpClient(config: ResolvedModelConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(config.requestTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(config.requestTimeoutSeconds * 2, TimeUnit.SECONDS)
                .build()
    }
}
