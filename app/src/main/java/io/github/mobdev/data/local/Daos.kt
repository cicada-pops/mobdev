package io.github.mobdev.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT name FROM channels ORDER BY name")
    fun observeNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY id ASC")
    fun observeByChannel(channel: String): Flow<List<MessageEntity>>

    @Query("SELECT MIN(id) FROM messages WHERE channel = :channel")
    suspend fun oldestId(channel: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE channel = :channel ORDER BY createdAt ASC, localId ASC")
    fun observeByChannel(channel: String): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC, localId ASC")
    suspend fun all(): List<OutboxEntity>

    @Insert
    suspend fun insert(message: OutboxEntity)

    @Query("DELETE FROM outbox WHERE localId = :localId")
    suspend fun deleteById(localId: Long)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}
