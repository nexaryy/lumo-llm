package me.proton.android.lumo.llm.client

import me.proton.android.lumo.llm.model.LlmProviderType
import me.proton.android.lumo.llm.model.ResolvedModelConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the right [LlmClient] implementation for the configured provider type and
 * spins up an OkHttpClient with the right timeouts.
 */
@Singleton
class LlmClientFactory @Inject constructor() {

    fun clientFor(config: ResolvedModelConfig): LlmClient {
        val http = when (config.providerType) {
            LlmProviderType.OPENAI_COMPATIBLE ->
                OpenAiCompatibleClient.buildHttpClient(config)
            LlmProviderType.ANTHROPIC ->
                AnthropicClient.buildHttpClient(config)
            LlmProviderType.CUSTOM ->
                CustomHttpClient.buildHttpClient(config)
        }
        return when (config.providerType) {
            LlmProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleClient(http)
            LlmProviderType.ANTHROPIC -> AnthropicClient(http)
            LlmProviderType.CUSTOM -> CustomHttpClient(http)
        }
    }
}
