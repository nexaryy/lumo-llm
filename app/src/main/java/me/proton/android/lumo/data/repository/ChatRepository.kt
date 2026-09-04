package me.proton.android.lumo.data.repository

import kotlinx.coroutines.flow.Flow
import me.proton.android.lumo.data.db.dao.ConversationDao
import me.proton.android.lumo.data.db.dao.MessageDao
import me.proton.android.lumo.data.db.entity.ConversationEntity
import me.proton.android.lumo.data.db.entity.MessageEntity
import me.proton.android.lumo.data.db.entity.MessageStatus
import me.proton.android.lumo.llm.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {

    fun observeConversations(lumoId: Long): Flow<List<ConversationEntity>> =
        conversationDao.observeByLumo(lumoId)

    fun observeConversation(id: Long): Flow<ConversationEntity?> =
        conversationDao.observeById(id)

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeByConversation(conversationId)

    suspend fun getConversation(id: Long): ConversationEntity? = conversationDao.getById(id)

    suspend fun createConversation(lumoId: Long, title: String = "New chat"): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insert(
            ConversationEntity(
                lumoId = lumoId,
                title = title,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun renameConversation(id: Long, title: String) =
        conversationDao.rename(id, title)

    suspend fun touchConversation(id: Long) = conversationDao.touch(id)

    suspend fun togglePinned(id: Long, pinned: Boolean) =
        conversationDao.setPinned(id, pinned)

    suspend fun deleteConversation(conversation: ConversationEntity) =
        conversationDao.delete(conversation)

    suspend fun appendMessage(
        conversationId: Long,
        role: String,
        content: String,
        status: MessageStatus = MessageStatus.DONE,
        model: String? = null,
        error: String? = null,
        elapsedMs: Long? = null,
    ): Long {
        touchConversation(conversationId)
        return messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                status = status,
                model = model,
                error = error,
                elapsedMs = elapsedMs,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateMessageContent(id: Long, content: String) =
        messageDao.updateContent(id, content)

    suspend fun finalizeMessage(
        id: Long,
        content: String,
        status: MessageStatus,
        error: String? = null,
        elapsedMs: Long? = null,
    ) = messageDao.finalize(id, content, status, error, elapsedMs)

    suspend fun deleteMessage(message: MessageEntity) = messageDao.delete(message)

    suspend fun clearConversation(conversationId: Long) =
        messageDao.deleteByConversation(conversationId)

    /**
     * Returns the message history as a list of [ChatMessage] ready to send to the LLM.
     * System prompt is NOT prepended here (the caller adds it from the Lumo config).
     */
    suspend fun historyFor(conversationId: Long): List<ChatMessage> =
        messageDao.getByConversation(conversationId)
            .filter { it.status == MessageStatus.DONE }
            .map { ChatMessage(role = it.role, content = it.content) }

    /**
     * Auto-titles a conversation from the first user message (only if it's still "New chat").
     */
    suspend fun maybeAutoTitle(conversationId: Long, firstUserMessage: String) {
        val conv = conversationDao.getById(conversationId) ?: return
        if (conv.title != "New chat") return
        val title = firstUserMessage.lineSequence().firstOrNull().orEmpty().take(60).trim()
        if (title.isNotBlank()) {
            conversationDao.rename(conversationId, title.ifBlank { "New chat" })
        }
    }
}
