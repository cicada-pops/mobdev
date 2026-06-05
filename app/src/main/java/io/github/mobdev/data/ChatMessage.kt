package io.github.mobdev.data

import io.github.mobdev.data.local.MessageEntity
import io.github.mobdev.data.local.OutboxEntity

data class ChatMessage(
    val key: String,
    val sortKey: Long,
    val from: String,
    val text: String?,
    val imageLink: String?,
    val time: Long,
    val pending: Boolean,
)

fun MessageEntity.toChatMessage(): ChatMessage = ChatMessage(
    key = "srv-$id",
    sortKey = id,
    from = from,
    text = text,
    imageLink = imageLink,
    time = time,
    pending = false,
)

fun OutboxEntity.toChatMessage(senderName: String): ChatMessage = ChatMessage(
    key = "out-$localId",
    sortKey = createdAt,
    from = senderName,
    text = text,
    imageLink = null,
    time = createdAt,
    pending = true,
)

fun Message.toEntity(channel: String): MessageEntity = MessageEntity(
    id = id,
    channel = channel,
    from = from,
    text = data.text?.text,
    imageLink = data.image?.link,
    time = time,
)
