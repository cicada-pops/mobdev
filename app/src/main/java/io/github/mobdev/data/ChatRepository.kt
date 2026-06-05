package io.github.mobdev.data

import io.github.mobdev.data.local.ChannelDao
import io.github.mobdev.data.local.ChannelEntity
import io.github.mobdev.data.local.MessageDao
import io.github.mobdev.data.local.OutboxDao
import io.github.mobdev.data.local.OutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

sealed class ChatException(message: String) : Exception(message) {

    class InvalidCredentials : ChatException("invalid credentials")

    class Unauthorized : ChatException("unauthorized")

    class Network : ChatException("network")

    class Unknown(cause: Throwable?) : ChatException(cause?.message ?: "unknown")
}

class ChatRepository(
    private val network: ChatNetwork,
    private val credentials: CredentialsStore,
    private val channelDao: ChannelDao,
    private val messageDao: MessageDao,
    private val outboxDao: OutboxDao,
) {

    private val flushMutex = Mutex()
    val savedUsername: String? get() = credentials.username
    val hasSavedCredentials: Boolean get() = credentials.hasCredentials

    fun thumbUrl(link: String): String = network.thumbUrl(link)
    fun imageUrl(link: String): String = network.imageUrl(link)

    fun observeChannels(): Flow<List<String>> = channelDao.observeNames()

    fun observeMessages(channel: String, senderName: String): Flow<List<ChatMessage>> =
        combine(
            messageDao.observeByChannel(channel),
            outboxDao.observeByChannel(channel),
        ) { cached, pending ->
            cached.map { it.toChatMessage() } + pending.map { it.toChatMessage(senderName) }
        }

    suspend fun refreshChannels() {
        val channels = guarded { network.api.channels() }
        channelDao.clear()
        channelDao.insertAll(channels.map { ChannelEntity(it) })
    }

    suspend fun refreshLatest(channel: String): Int {
        val messages = guarded {
            network.api.channelMessages(channel, PAGE_SIZE, NEWEST, reverse = true)
        }
        messageDao.upsertAll(messages.map { it.toEntity(channel) })
        return messages.size
    }

    suspend fun loadOlder(channel: String): Int {
        val beforeId = messageDao.oldestId(channel) ?: NEWEST
        val messages = guarded {
            network.api.channelMessages(channel, PAGE_SIZE, beforeId, reverse = true)
        }
        messageDao.upsertAll(messages.map { it.toEntity(channel) })
        return messages.size
    }

    suspend fun enqueue(channel: String, from: String, text: String, now: Long) {
        outboxDao.insert(
            OutboxEntity(channel = channel, from = from, text = text, createdAt = now),
        )
    }

    suspend fun flushOutbox() = flushMutex.withLock {
        for (pending in outboxDao.all()) {
            try {
                sendOverNetwork(pending.channel, pending.from, pending.text)
                outboxDao.deleteById(pending.localId)
            } catch (_: ChatException.Network) {
                return@withLock
            }
        }
    }

    private suspend fun sendOverNetwork(channel: String, from: String, text: String) {
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

    fun logout() = credentials.clear()

    suspend fun clearCache() {
        outboxDao.clear()
        messageDao.clear()
        channelDao.clear()
    }

    private inline fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (e: HttpException) {
            throw e.toChatException()
        } catch (e: IOException) {
            throw ChatException.Network().initCausedBy(e)
        }

    private companion object {
        const val PAGE_SIZE = 20
        const val NEWEST = Long.MAX_VALUE
    }
}

private fun HttpException.toChatException(): ChatException =
    if (code() == 401) ChatException.Unauthorized() else ChatException.Unknown(this)

private fun ChatException.initCausedBy(cause: Throwable): ChatException =
    apply { initCause(cause) }
