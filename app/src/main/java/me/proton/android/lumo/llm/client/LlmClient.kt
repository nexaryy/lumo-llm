package me.proton.android.lumo.llm.client

import kotlinx.coroutines.flow.Flow
import me.proton.android.lumo.llm.model.ChatMessage
import me.proton.android.lumo.llm.model.ResolvedModelConfig

/**
 * Result of one streaming chunk from the LLM.
 * - [Delta] — incremental text fragment (streaming).
 * - [Done]  — model finished, includes final elapsedMs.
 * - [Error] — model/API returned an error.
 */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Done(val fullText: String, val elapsedMs: Long) : StreamEvent
    data class Error(val message: String, val throwable: Throwable? = null) : StreamEvent
}

interface LlmClient {
    /**
     * Send the conversation (with system prompt already prepended) and return a flow of events.
     * The flow MUST emit at least one [StreamEvent.Done] or [StreamEvent.Error] before completing.
     */
    fun stream(
        messages: List<ChatMessage>,
        config: ResolvedModelConfig,
    ): Flow<StreamEvent>
}
