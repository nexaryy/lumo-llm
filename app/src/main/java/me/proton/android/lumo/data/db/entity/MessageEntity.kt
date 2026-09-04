package me.proton.android.lumo.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index(value = ["conversationId", "createdAt"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val conversationId: Long,
    /** "user", "assistant", "system". */
    val role: String,
    /** Full text content. Streaming deltas are appended in place. */
    val content: String,
    /** Optional error message (only set if status = ERROR). */
    val error: String? = null,
    /** Optional model identifier that produced this message. */
    val model: String? = null,
    /** milliseconds taken by the model to produce this message (assistant only). */
    val elapsedMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.DONE,
)

enum class MessageStatus {
    /** Message is being streamed from the API. */
    STREAMING,
    /** Message is complete and persisted. */
    DONE,
    /** The API returned an error; see [MessageEntity.error]. */
    ERROR,
}
