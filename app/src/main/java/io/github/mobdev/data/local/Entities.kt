package io.github.mobdev.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val name: String,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Long,
    val channel: String,
    val from: String,
    val text: String?,
    val imageLink: String?,
    val time: Long,
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val channel: String,
    val from: String,
    val text: String,
    val createdAt: Long,
)
