package me.proton.android.lumo.chat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.android.lumo.R
import me.proton.android.lumo.data.db.entity.ConversationEntity
import me.proton.android.lumo.data.db.entity.LumoEntity
import me.proton.android.lumo.data.db.entity.MessageEntity
import me.proton.android.lumo.data.db.entity.MessageStatus
import me.proton.android.lumo.data.repository.ApiConfigRepository
import me.proton.android.lumo.data.repository.ChatRepository
import me.proton.android.lumo.data.repository.LumoRepository
import me.proton.android.lumo.llm.client.LlmClientFactory
import me.proton.android.lumo.llm.client.StreamEvent
import me.proton.android.lumo.llm.model.ChatMessage
import me.proton.android.lumo.llm.model.LlmProviderType
import me.proton.android.lumo.notification.LumoNotifier
import me.proton.android.lumo.speech.SpeechRepository
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "ChatViewModel"

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val app: Application,
    private val lumoRepository: LumoRepository,
    private val chatRepository: ChatRepository,
    private val apiConfigRepository: ApiConfigRepository,
    private val clientFactory: LlmClientFactory,
    private val notifier: LumoNotifier,
    private val speechRepository: SpeechRepository,
) : AndroidViewModel(app) {

    data class ChatUiState(
        val currentLumo: LumoEntity? = null,
        val conversations: List<ConversationEntity> = emptyList(),
        val currentConversation: ConversationEntity? = null,
        val messages: List<MessageEntity> = emptyList(),
        val isGenerating: Boolean = false,
        val showApiConfigBanner: Boolean = false,
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChatEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var currentLumoId: Long? = null
    private var currentConversationId: Long? = null
    private var streamJob: Job? = null
    private var lumoObserverJob: Job? = null
    private var conversationObserverJob: Job? = null
    private var messageObserverJob: Job? = null

    init {
        observeSpeech()
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            lumoRepository.observeAll().collect { lumos ->
                val lumo = lumos.firstOrNull { it.id == currentLumoId }
                    ?: lumos.firstOrNull()
                _uiState.update { it.copy(currentLumo = lumo) }
                if (lumo != null && currentLumoId != lumo.id) {
                    selectLumo(lumo.id)
                }
            }
        }
    }

    private fun observeSpeech() {
        viewModelScope.launch {
            speechRepository.emittedText().collect { transcript ->
                _events.trySend(ChatEvent.SpeechTranscript(transcript))
            }
        }
    }

    fun selectLumo(lumoId: Long) {
        if (currentLumoId == lumoId) return
        currentLumoId = lumoId
        currentConversationId = null
        messageObserverJob?.cancel()
        lumoObserverJob?.cancel()
        lumoObserverJob = viewModelScope.launch {
            chatRepository.observeConversations(lumoId).collect { convs ->
                val current = convs.firstOrNull { it.id == currentConversationId }
                    ?: convs.firstOrNull()
                _uiState.update {
                    it.copy(
                        conversations = convs,
                        currentConversation = current,
                        messages = emptyList(),
                    )
                }
                if (current != null && currentConversationId != current.id) {
                    selectConversation(current.id)
                }
            }
        }
    }

    fun selectConversation(conversationId: Long) {
        if (currentConversationId == conversationId) return
        currentConversationId = conversationId
        messageObserverJob?.cancel()
        messageObserverJob = viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun newConversation() {
        val lumoId = currentLumoId ?: return
        viewModelScope.launch {
            val id = chatRepository.createConversation(lumoId)
            selectConversation(id)
        }
    }

    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversation)
        }
    }

    fun togglePin(conversation: ConversationEntity) {
        viewModelScope.launch {
            chatRepository.togglePinned(conversation.id, !conversation.pinned)
        }
    }

    fun renameConversation(conversation: ConversationEntity, title: String) {
        viewModelScope.launch {
            chatRepository.renameConversation(conversation.id, title)
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        val lumoId = currentLumoId ?: return
        val convId = currentConversationId

        viewModelScope.launch {
            val apiConfig = apiConfigRepository.current()
            val showBanner = apiConfig.apiKey.isBlank() &&
                apiConfig.providerType != LlmProviderType.CUSTOM
            _uiState.update { it.copy(isGenerating = true, showApiConfigBanner = showBanner) }

            val actualConvId = convId ?: chatRepository.createConversation(lumoId, "New chat")
            if (convId == null) selectConversation(actualConvId)

            // 1. Persist the user message
            chatRepository.appendMessage(
                conversationId = actualConvId,
                role = "user",
                content = text,
                status = MessageStatus.DONE,
            )
            chatRepository.maybeAutoTitle(actualConvId, text)

            // 2. Show streaming notification
            val lumo = lumoRepository.getById(lumoId)
            val lumoName = lumo?.name ?: app.getString(R.string.app_name)
            startForegroundService(actualConvId, lumoName)

            // 3. Persist the assistant placeholder (streaming)
            val assistantMsgId = chatRepository.appendMessage(
                conversationId = actualConvId,
                role = "assistant",
                content = "",
                status = MessageStatus.STREAMING,
                model = lumo?.modelName ?: apiConfig.defaultModel,
            )

            // 4. Resolve the effective config
            val lumoEntity = lumo ?: return@launch
            val resolvedConfig = lumoRepository.resolveConfig(lumoEntity)

            // 5. Build the conversation history
            val history = chatRepository.historyFor(actualConvId)
            val messages = buildList {
                if (resolvedConfig.providerType != LlmProviderType.ANTHROPIC &&
                    resolvedConfig.systemPrompt.isNotBlank()
                ) {
                    add(ChatMessage(role = "system", content = resolvedConfig.systemPrompt))
                }
                // For Anthropic, system prompt is handled by the client itself.
                addAll(history.filter { it.role != "system" })
            }

            // 6. Stream the response
            streamJob = viewModelScope.launch {
                val client = clientFactory.clientFor(resolvedConfig)
                val accumulated = StringBuilder()
                var firstError: String? = null

                client.stream(messages, resolvedConfig).collect { ev ->
                    when (ev) {
                        is StreamEvent.Delta -> {
                            accumulated.append(ev.text)
                            chatRepository.updateMessageContent(assistantMsgId, accumulated.toString())
                        }
                        is StreamEvent.Done -> {
                            val finalText = ev.fullText.ifBlank { accumulated.toString() }
                            chatRepository.finalizeMessage(
                                id = assistantMsgId,
                                content = finalText,
                                status = MessageStatus.DONE,
                                elapsedMs = ev.elapsedMs,
                            )
                            notifier.showDoneNotification(
                                conversationId = actualConvId,
                                lumoName = lumoName,
                                success = true,
                                preview = finalText,
                            )
                            stopForegroundService(actualConvId)
                            _uiState.update { it.copy(isGenerating = false) }
                        }
                        is StreamEvent.Error -> {
                            firstError = firstError ?: ev.message
                            chatRepository.finalizeMessage(
                                id = assistantMsgId,
                                content = accumulated.toString(),
                                status = MessageStatus.ERROR,
                                error = ev.message,
                                elapsedMs = null,
                            )
                            notifier.showDoneNotification(
                                conversationId = actualConvId,
                                lumoName = lumoName,
                                success = false,
                                preview = ev.message,
                            )
                            stopForegroundService(actualConvId)
                            _uiState.update { it.copy(isGenerating = false) }
                            _events.trySend(ChatEvent.ShowError(ev.message))
                        }
                    }
                }
            }
        }
    }

    fun cancelStreaming() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update { it.copy(isGenerating = false) }
        currentConversationId?.let { stopForegroundService(it) }
    }

    private fun startForegroundService(conversationId: Long, lumoName: String) {
        val intent = android.content.Intent(app, me.proton.android.lumo.chat.LlmResponseService::class.java).apply {
            putExtra(me.proton.android.lumo.chat.LlmResponseService.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(me.proton.android.lumo.chat.LlmResponseService.EXTRA_LUMO_NAME, lumoName)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    private fun stopForegroundService(conversationId: Long) {
        notifier.cancelStreamingNotification(conversationId)
        val intent = android.content.Intent(app, me.proton.android.lumo.chat.LlmResponseService::class.java)
        app.stopService(intent)
    }

    sealed interface ChatEvent {
        data class ShowError(val message: String) : ChatEvent
        data class SpeechTranscript(val text: String) : ChatEvent
    }
}
