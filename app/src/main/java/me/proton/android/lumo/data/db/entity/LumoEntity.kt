package me.proton.android.lumo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A "Lumo" is an assistant personality: name, system prompt, model config, accent color.
 * The user can create as many Lumos as they want — like ChatGPT Custom GPTs.
 */
@Entity(
    tableName = "lumos",
    indices = [Index(value = ["createdAt"])]
)
data class LumoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    /** System / "developer" prompt prepended to every conversation. */
    val systemPrompt: String = "",
    /** Override model name, e.g. "gpt-4o-mini", "claude-3-5-sonnet-20241022", "llama3.2". */
    val modelName: String? = null,
    /** Override temperature (0..2). null = use global default. */
    val temperature: Float? = null,
    /** Hex color string used as the avatar background. */
    val accentColor: String = "#6D4AFF",
    /** Emoji or short text shown on the avatar tile. */
    val avatarTag: String = "🐱",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)
