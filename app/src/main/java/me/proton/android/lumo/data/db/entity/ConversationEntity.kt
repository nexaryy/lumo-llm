package me.proton.android.lumo.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A chat thread inside a Lumo. A Lumo can have many conversations (e.g. "Trip planning",
 * "Code review", "Random chat"). Chats are NEVER auto-deleted — the user explicitly asked
 * for "no 7-day deletion".
 */
@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = LumoEntity::class,
            parentColumns = ["id"],
            childColumns = ["lumoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lumoId"), Index(value = ["lumoId", "updatedAt"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val lumoId: Long,
    val title: String = "New chat",
    /** Optional pinned flag for the drawer. */
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
