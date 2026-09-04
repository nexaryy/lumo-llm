package me.proton.android.lumo.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Provider type — picks the right request/response shape on the client side.
 *
 * - [OPENAI_COMPATIBLE] works for OpenAI, OpenRouter, Together, Groq, LM Studio, vLLM, Ollama (with /v1),
 *   DeepSeek, Mistral, xAI, etc. — anything that speaks the OpenAI Chat Completions API.
 * - [ANTHROPIC] uses the Messages API (x-api-key + anthropic-version headers).
 * - [CUSTOM] is a totally generic HTTP template: you provide URL, method, headers and a body
 *   template with `{{prompt}}`, `{{system}}`, `{{history}}` placeholders and a JSONPath-ish
 *   expression for extracting the answer from the response. Equivalent to `curl`-ing your model.
 */
enum class LlmProviderType {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    CUSTOM,
}

@Serializable
data class ApiConfig(
    val providerType: LlmProviderType = LlmProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    /** Default model name when a Lumo doesn't override it. */
    val defaultModel: String = "gpt-4o-mini",
    val defaultTemperature: Float = 0.7f,
    val defaultMaxTokens: Int = 2048,
    val requestTimeoutSeconds: Long = 60,
    val connectTimeoutSeconds: Long = 30,

    /** Extra headers as raw key:value lines, one per line. */
    val extraHeadersRaw: String = "",

    /** CUSTOM provider only. Body template with placeholders {{prompt}}, {{system}}, {{history}}. */
    val customBodyTemplate: String = "",
    /** CUSTOM provider only. HTTP method, default POST. */
    val customMethod: String = "POST",
    /** CUSTOM provider only. JSONPath-ish expression to extract the final text from the response JSON. */
    val customResponsePath: String = "\$.choices[0].message.content",
)

/**
 * Per-Lumo effective settings (after merging global defaults + Lumo overrides).
 */
data class ResolvedModelConfig(
    val providerType: LlmProviderType,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val systemPrompt: String,
    val extraHeaders: Map<String, String>,
    val customBodyTemplate: String,
    val customMethod: String,
    val customResponsePath: String,
    val requestTimeoutSeconds: Long,
    val connectTimeoutSeconds: Long,
)

/**
 * One message in the OpenAI-style chat payload.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OpenAiChoiceMessage(
    val role: String? = null,
    val content: String? = null,
)

@Serializable
data class OpenAiChoice(
    val index: Int? = null,
    @SerialName("delta")
    val delta: OpenAiChoiceMessage? = null,
    @SerialName("message")
    val message: OpenAiChoiceMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class OpenAiChunk(
    val id: String? = null,
    val choices: List<OpenAiChoice> = emptyList(),
    val error: JsonElement? = null,
)

@Serializable
data class AnthropicContentBlock(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
data class AnthropicDelta(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
data class AnthropicStreamEvent(
    val type: String? = null,
    val delta: AnthropicDelta? = null,
    @SerialName("content_block")
    val contentBlock: AnthropicContentBlock? = null,
    val message: AnthropicContentBlock? = null,
    val error: JsonElement? = null,
)
