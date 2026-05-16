package io.github.mobdev.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /login`. */
@Serializable
data class LoginRequest(
    val name: String,
    val pwd: String,
)

/**
 * A single message as returned by the channel/inbox endpoints.
 *
 * The server sends numeric [id] and [time] (milliseconds since the epoch);
 * the documentation's "string" wording does not match the live API.
 */
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

/**
 * Payload wrapper: the server uses a tagged object that contains
 * exactly one of `Text` or `Image`.
 */
@Serializable
data class MessageData(
    @SerialName("Text") val text: TextContent? = null,
    @SerialName("Image") val image: ImageContent? = null,
)

@Serializable
data class TextContent(val text: String)

@Serializable
data class ImageContent(val link: String)

/** Body of `POST /messages` when sending a text message. */
@Serializable
data class OutgoingMessage(
    val from: String,
    val to: String,
    val data: MessageData,
)
