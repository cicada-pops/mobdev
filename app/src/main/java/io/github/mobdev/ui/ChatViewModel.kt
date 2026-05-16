package io.github.mobdev.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mobdev.data.ChatException
import io.github.mobdev.data.ChatNetwork
import io.github.mobdev.data.ChatRepository
import io.github.mobdev.data.CredentialsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds all chat state. Because it is a [androidx.lifecycle.ViewModel] it
 * survives configuration changes, so a rotation never re-fetches anything:
 * the open chat, loaded messages and the open image are all kept here and
 * the UI just re-reads them.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val credentials = CredentialsStore(application)
    private val repository = ChatRepository(ChatNetwork(credentials), credentials)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        if (repository.hasSavedCredentials) {
            // Login screen is skipped when credentials were already entered.
            _state.update { it.copy(phase = Phase.Startup, isAuthenticating = true) }
            viewModelScope.launch {
                val name = credentials.username.orEmpty()
                val password = credentials.password.orEmpty()
                try {
                    repository.login(name, password)
                    _state.update {
                        it.copy(
                            phase = Phase.Ready,
                            username = name,
                            password = password,
                            isAuthenticating = false,
                        )
                    }
                    loadChannels()
                } catch (e: ChatException) {
                    _state.update {
                        it.copy(
                            phase = Phase.Login,
                            username = name,
                            password = "",
                            isAuthenticating = false,
                            loginError = e.toLoginErrorKind(),
                        )
                    }
                }
            }
        } else {
            _state.update { it.copy(phase = Phase.Login) }
        }
    }

    // ---- Login screen ----------------------------------------------------

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
                loadChannels()
            } catch (e: ChatException) {
                _state.update {
                    it.copy(isAuthenticating = false, loginError = e.toLoginErrorKind())
                }
            }
        }
    }

    // ---- Channels --------------------------------------------------------

    fun loadChannels() {
        if (_state.value.isLoadingChannels) return
        _state.update { it.copy(isLoadingChannels = true, channelsError = false) }
        viewModelScope.launch {
            try {
                val channels = repository.loadChannels()
                _state.update {
                    it.copy(channels = channels, isLoadingChannels = false)
                }
            } catch (e: ChatException) {
                if (e is ChatException.Unauthorized) {
                    forceRelogin()
                } else {
                    _state.update {
                        it.copy(isLoadingChannels = false, channelsError = true)
                    }
                }
            }
        }
    }

    // ---- Messages --------------------------------------------------------

    fun selectChannel(channel: String) {
        _state.update {
            it.copy(
                selectedChannel = channel,
                messages = emptyList(),
                isLoadingMessages = true,
                messagesError = false,
                isLoadingOlder = false,
                hasMoreOlder = true,
                draft = "",
            )
        }
        viewModelScope.launch { fetchLatest(channel) }
    }

    fun closeChannel() {
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
        _state.update { it.copy(isLoadingMessages = true, messagesError = false) }
        viewModelScope.launch { fetchLatest(channel) }
    }

    private suspend fun fetchLatest(channel: String) {
        try {
            val messages = repository.loadLatest(channel)
            // Ignore a stale response if the user already switched channels.
            if (_state.value.selectedChannel != channel) return
            _state.update {
                it.copy(
                    messages = messages,
                    isLoadingMessages = false,
                    hasMoreOlder = messages.size >= PAGE_SIZE,
                )
            }
        } catch (e: ChatException) {
            if (e is ChatException.Unauthorized) {
                forceRelogin()
            } else if (_state.value.selectedChannel == channel) {
                _state.update { it.copy(isLoadingMessages = false, messagesError = true) }
            }
        }
    }

    /** Loads the previous page; triggered only by an explicit user tap so a
     *  rotation can never cause a network call. */
    fun loadOlderMessages() {
        val current = _state.value
        val channel = current.selectedChannel ?: return
        if (current.isLoadingOlder || !current.hasMoreOlder || current.messages.isEmpty()) return
        val oldestId = current.messages.first().id
        _state.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            try {
                val older = repository.loadOlder(channel, oldestId)
                if (_state.value.selectedChannel != channel) return@launch
                _state.update { state ->
                    val known = state.messages.mapTo(HashSet()) { it.id }
                    val merged = older.filter { it.id !in known } + state.messages
                    state.copy(
                        messages = merged,
                        isLoadingOlder = false,
                        hasMoreOlder = older.size >= PAGE_SIZE,
                    )
                }
            } catch (e: ChatException) {
                if (e is ChatException.Unauthorized) {
                    forceRelogin()
                } else {
                    _state.update { it.copy(isLoadingOlder = false) }
                }
            }
        }
    }

    // ---- Sending ---------------------------------------------------------

    fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

    fun sendDraft() {
        val current = _state.value
        val channel = current.selectedChannel ?: return
        val text = current.draft.trim()
        if (text.isEmpty() || current.isSending) return
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                repository.send(channel, current.username, text)
                _state.update { it.copy(draft = "", isSending = false) }
                if (_state.value.selectedChannel == channel) fetchLatest(channel)
            } catch (e: ChatException) {
                if (e is ChatException.Unauthorized) {
                    forceRelogin()
                } else {
                    _state.update { it.copy(isSending = false, messagesError = true) }
                }
            }
        }
    }

    // ---- Image -----------------------------------------------------------

    fun openImage(link: String) = _state.update { it.copy(fullscreenImage = link) }

    fun closeImage() = _state.update { it.copy(fullscreenImage = null) }

    // ---- Session ---------------------------------------------------------

    fun logout() {
        repository.logout()
        _state.value = ChatUiState(phase = Phase.Login)
    }

    /** A 401 that survived automatic re-login: restart the login process. */
    private fun forceRelogin() {
        _state.update {
            it.copy(
                phase = Phase.Login,
                password = "",
                isAuthenticating = false,
                isLoadingChannels = false,
                isLoadingMessages = false,
                isLoadingOlder = false,
                isSending = false,
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
