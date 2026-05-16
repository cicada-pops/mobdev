package io.github.mobdev.data

import retrofit2.HttpException
import java.io.IOException

/** Typed failures the UI can react to without inspecting HTTP internals. */
sealed class ChatException(message: String) : Exception(message) {
    /** Wrong login/password supplied on the login screen. */
    class InvalidCredentials : ChatException("invalid credentials")

    /** A request failed authentication and re-login did not help. */
    class Unauthorized : ChatException("unauthorized")

    /** No network / server unreachable. */
    class Network : ChatException("network")

    /** Anything else. */
    class Unknown(cause: Throwable?) : ChatException(cause?.message ?: "unknown")
}

/**
 * Single entry point for chat data. Maps transport errors to [ChatException]
 * and keeps the token / credentials store in sync.
 */
class ChatRepository(
    private val network: ChatNetwork,
    private val credentials: CredentialsStore,
) {
    val savedUsername: String? get() = credentials.username
    val hasSavedCredentials: Boolean get() = credentials.hasCredentials

    fun thumbUrl(link: String): String = network.thumbUrl(link)
    fun imageUrl(link: String): String = network.imageUrl(link)

    /** Logs in and, on success, persists credentials + token. */
    suspend fun login(username: String, password: String) {
        val response = try {
            network.api.login(LoginRequest(username, password))
        } catch (e: IOException) {
            throw ChatException.Network().initCausedBy(e)
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            if (response.code() == 401 || response.code() == 403) {
                throw ChatException.InvalidCredentials()
            }
            throw ChatException.Unknown(HttpException(response))
        }
        val headerToken = response.headers()[AUTH_TOKEN_HEADER]
        val token = if (headerToken != null) {
            response.body()?.close()
            headerToken
        } else {
            response.body()?.string()?.trim()?.takeIf { it.isNotEmpty() }
        }
        if (token.isNullOrEmpty()) throw ChatException.Unknown(null)
        credentials.saveCredentials(username, password)
        credentials.token = token
    }

    suspend fun loadChannels(): List<String> = guarded { network.api.channels() }

    /** Newest [DEFAULT_PAGE] messages of [channel], oldest-first for display. */
    suspend fun loadLatest(channel: String): List<Message> = guarded {
        network.api
            .channelMessages(channel, DEFAULT_PAGE, NEWEST, reverse = true)
            .sortedBy { it.id }
    }

    /** Up to [DEFAULT_PAGE] messages older than [beforeId], oldest-first. */
    suspend fun loadOlder(channel: String, beforeId: Long): List<Message> = guarded {
        network.api
            .channelMessages(channel, DEFAULT_PAGE, beforeId, reverse = true)
            .sortedBy { it.id }
    }

    suspend fun send(channel: String, from: String, text: String) {
        val message = OutgoingMessage(
            from = from,
            to = channel,
            data = MessageData(text = TextContent(text)),
        )
        val response = try {
            network.api.sendMessage(message)
        } catch (e: HttpException) {
            throw e.toChatException()
        } catch (e: IOException) {
            throw ChatException.Network().initCausedBy(e)
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            if (response.code() == 401) throw ChatException.Unauthorized()
            throw ChatException.Unknown(HttpException(response))
        }
        response.body()?.close()
    }

    fun logout() = credentials.clear()

    private inline fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (e: HttpException) {
            throw e.toChatException()
        } catch (e: IOException) {
            throw ChatException.Network().initCausedBy(e)
        }

    private companion object {
        const val DEFAULT_PAGE = 20
        const val NEWEST = Long.MAX_VALUE
    }
}

private fun HttpException.toChatException(): ChatException =
    if (code() == 401) ChatException.Unauthorized() else ChatException.Unknown(this)

private fun ChatException.initCausedBy(cause: Throwable): ChatException =
    apply { initCause(cause) }
