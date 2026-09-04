package me.proton.android.lumo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.proton.android.lumo.data.db.entity.MessageEntity
import me.proton.android.lumo.data.db.entity.MessageStatus

@Dao
interface MessageDao {

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query(
        """
        UPDATE messages
        SET content = :content, status = :status, error = :error, elapsedMs = :elapsedMs
        WHERE id = :id
        """
    )
    suspend fun finalize(
        id: Long,
        content: String,
        status: MessageStatus,
        error: String?,
        elapsedMs: Long?
    )

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)
}
