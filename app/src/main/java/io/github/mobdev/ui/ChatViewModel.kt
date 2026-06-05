package io.github.mobdev.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mobdev.data.ChatException
import io.github.mobdev.data.ChatNetwork
import io.github.mobdev.data.ChatRepository
import io.github.mobdev.data.CredentialsStore
import io.github.mobdev.data.NetworkMonitor
import io.github.mobdev.data.local.ChatDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val credentials = CredentialsStore(application)
    private val database = ChatDatabase.get(application)
    private val repository = ChatRepository(
        network = ChatNetwork(credentials),
        credentials = credentials,
        channelDao = database.channelDao(),
        messageDao = database.messageDao(),
        outboxDao = database.outboxDao(),
    )
    private val networkMonitor = NetworkMonitor(application)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var channelsJob: Job? = null
    private var messagesJob: Job? = null

    init {
        observeConnectivity()
        if (repository.hasSavedCredentials) {
            val name = credentials.username.orEmpty()
            _state.update { it.copy(phase = Phase.Ready, username = name) }
            startObservingChannels()
            viewModelScope.launch { primeSession(name, credentials.password.orEmpty()) }
        } else {
            _state.update { it.copy(phase = Phase.Login) }
        }
    }

    private suspend fun primeSession(name: String, password: String) {
        refreshChannelsCatching()
        try {
            repository.login(name, password)
        } catch (e: ChatException) {

            if (e is ChatException.InvalidCredentials) {
                forceRelogin()
                return
            }
        }
        flushOutboxCatching()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                val wasOnline = _state.value.isOnline
                _state.update { it.copy(isOnline = online) }
                if (online && !wasOnline) onReconnected()
            }
        }
    }

    private fun onReconnected() {
        if (_state.value.phase != Phase.Ready) return
        viewModelScope.launch {
            flushOutboxCatching()
            refreshChannelsCatching()
            _state.value.selectedChannel?.let { refreshMessages(it) }
        }
    }

    private suspend fun refreshChannelsCatching() {
        try {
            repository.refreshChannels()
        } catch (e: ChatException) {
            if (e is ChatException.Unauthorized) forceRelogin()
        }
    }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }

    fun dismissLoginError() = _state.update { it.copy(loginError = null) }

    fun submitLogin() {
        val current = _state.value
        if (current.isAuthenticating) return
        val name = current.username.trim()
        val password = current.password
        if (name.isEmpty() || password.isEmpty()) {
            _state.update { it.copy(loginError = LoginErrorKind.InvalidCredentials) }
            return
        }
        _state.update { it.copy(isAuthenticating = true) }
        viewModelScope.launch {
            try {
                repository.login(name, password)
                _state.update {
                    it.copy(phase = Phase.Ready, username = name, isAuthenticating = false)
                }
                startObservingChannels()
                refreshChannelsCatching()
                flushOutboxCatching()
            } catch (e: ChatException) {
                _state.update {
                    it.copy(isAuthenticating = false, loginError = e.toLoginErrorKind())
                }
            }
        }
    }

    private fun startObservingChannels() {
        if (channelsJob != null) return
        channelsJob = viewModelScope.launch {
            repository.observeChannels().collect { channels ->
                _state.update { it.copy(channels = channels) }
            }
        }
    }

    fun refreshChannels() {
        if (!_state.value.isOnline) return
        _state.update { it.copy(isRefreshingChannels = true) }
        viewModelScope.launch {
            try {
                repository.refreshChannels()
            } catch (e: ChatException) {
                if (e is ChatException.Unauthorized) forceRelogin()
            } finally {
                _state.update { it.copy(isRefreshingChannels = false) }
            }
        }
    }

    fun selectChannel(channel: String) {
        val online = _state.value.isOnline
        _state.update {
            it.copy(
                selectedChannel = channel,
                messages = emptyList(),
                isRefreshingMessages = online,
                messagesError = false,
                isLoadingOlder = false,
                hasMoreOlder = true,
                draft = "",
            )
        }
        observeChannelMessages(channel)
        if (online) viewModelScope.launch { refreshMessages(channel) }
    }

    private fun observeChannelMessages(channel: String) {
        messagesJob?.cancel()
        val sender = _state.value.username
        messagesJob = viewModelScope.launch {
            repository.observeMessages(channel, sender).collect { messages ->
                if (_state.value.selectedChannel != channel) return@collect
                _state.update { it.copy(messages = messages) }
            }
        }
    }

    fun closeChannel() {
        messagesJob?.cancel()
        messagesJob = null
        _state.update {
            it.copy(
                selectedChannel = null,
                messages = emptyList(),
                messagesError = false,
                draft = "",
                fullscreenImage = null,
            )
        }
    }

    fun retryMessages() {
        val channel = _state.value.selectedChannel ?: return
        if (!_state.value.isOnline) return
        _state.update { it.copy(isRefreshingMessages = true, messagesError = false) }
        viewModelScope.launch { refreshMessages(channel) }
    }

    private suspend fun refreshMessages(channel: String) {
        try {
            val count = repository.refreshLatest(channel)
            if (_state.value.selectedChannel != channel) return
            _state.update {
                it.copy(isRefreshingMessages = false, hasMoreOlder = count >= PAGE_SIZE)
            }
        } catch (e: ChatException) {
            if (e is ChatException.Unauthorized) {
                forceRelogin()
            } else if (_state.value.selectedChannel == channel) {

                _state.update {
                    it.copy(
                        isRefreshingMessages = false,
                        messagesError = e !is ChatException.Network,
                    )
                }
            }
        }
    }

    fun loadOlderMessages() {
        val current = _state.value
        val channel = current.selectedChannel ?: return
        if (current.isLoadingOlder || !current.hasMoreOlder || !current.isOnline) return
        _state.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            try {
                val count = repository.loadOlder(channel)
                if (_state.value.selectedChannel != channel) return@launch
                _state.update {
                    it.copy(isLoadingOlder = false, hasMoreOlder = count >= PAGE_SIZE)
                }
            } catch (e: ChatException) {
                if (e is ChatException.Unauthorized) forceRelogin()
                else _state.update { it.copy(isLoadingOlder = false) }
            }
        }
    }

    fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

    fun sendDraft() {
        val current = _state.value
        val channel = current.selectedChannel ?: return
        val text = current.draft.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {

            repository.enqueue(channel, current.username, text, System.currentTimeMillis())
            if (_state.value.isOnline) {
                flushOutboxCatching()
                if (_state.value.selectedChannel == channel) refreshMessages(channel)
            }
        }
    }

    private suspend fun flushOutboxCatching() {
        try {
            repository.flushOutbox()
        } catch (e: ChatException) {
            if (e is ChatException.Unauthorized) forceRelogin()
        }
    }

    fun openImage(link: String) = _state.update { it.copy(fullscreenImage = link) }

    fun closeImage() = _state.update { it.copy(fullscreenImage = null) }

    fun logout() {
        channelsJob?.cancel(); channelsJob = null
        messagesJob?.cancel(); messagesJob = null
        repository.logout()
        viewModelScope.launch { repository.clearCache() }
        _state.value = ChatUiState(phase = Phase.Login, isOnline = _state.value.isOnline)
    }

    private fun forceRelogin() {
        messagesJob?.cancel(); messagesJob = null
        _state.update {
            it.copy(
                phase = Phase.Login,
                password = "",
                isAuthenticating = false,
                isRefreshingChannels = false,
                isRefreshingMessages = false,
                isLoadingOlder = false,
                selectedChannel = null,
                messages = emptyList(),
                fullscreenImage = null,
                loginError = LoginErrorKind.InvalidCredentials,
            )
        }
    }

    fun thumbUrl(link: String): String = repository.thumbUrl(link)
    fun imageUrl(link: String): String = repository.imageUrl(link)

    private companion object {
        const val PAGE_SIZE = 20
    }
}

private fun ChatException.toLoginErrorKind(): LoginErrorKind = when (this) {
    is ChatException.InvalidCredentials, is ChatException.Unauthorized ->
        LoginErrorKind.InvalidCredentials

    is ChatException.Network -> LoginErrorKind.Network
    is ChatException.Unknown -> LoginErrorKind.Unknown
}
