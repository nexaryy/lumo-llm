package me.proton.android.lumo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import me.proton.android.lumo.data.db.dao.ConversationDao
import me.proton.android.lumo.data.db.dao.LumoDao
import me.proton.android.lumo.data.db.dao.MessageDao
import me.proton.android.lumo.data.db.entity.ConversationEntity
import me.proton.android.lumo.data.db.entity.LumoEntity
import me.proton.android.lumo.data.db.entity.MessageEntity
import me.proton.android.lumo.data.db.entity.MessageStatus

class MessageStatusConverter {
    @TypeConverter
    fun fromStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): MessageStatus =
        runCatching { MessageStatus.valueOf(value) }.getOrDefault(MessageStatus.DONE)
}

@Database(
    entities = [
        LumoEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(MessageStatusConverter::class)
abstract class LumoDatabase : RoomDatabase() {
    abstract fun lumoDao(): LumoDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        const val NAME = "lumo-chat.db"
    }
}
