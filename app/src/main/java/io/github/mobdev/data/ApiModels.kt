package io.github.mobdev.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val name: String,
    val pwd: String,
)

@Serializable
data class Message(
    val id: Long,
    val from: String,
    val to: String = DEFAULT_CHANNEL,
    val data: MessageData,
    val time: Long = 0L,
) {
    companion object {
        const val DEFAULT_CHANNEL = "1@channel"
    }
}

@Serializable
data class MessageData(
    @SerialName("Text") val text: TextContent? = null,
    @SerialName("Image") val image: ImageContent? = null,
)

@Serializable
data class TextContent(val text: String)

@Serializable
data class ImageContent(val link: String)

@Serializable
data class OutgoingMessage(
    val from: String,
    val to: String,
    val data: MessageData,
)
