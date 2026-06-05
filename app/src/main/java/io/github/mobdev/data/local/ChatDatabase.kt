package io.github.mobdev.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChannelEntity::class, MessageEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile
        private var instance: ChatDatabase? = null

        fun get(context: Context): ChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat.db",
                ).build().also { instance = it }
            }
    }
}
