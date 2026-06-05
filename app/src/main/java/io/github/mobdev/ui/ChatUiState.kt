package io.github.mobdev.ui

import io.github.mobdev.data.ChatMessage

enum class Phase {

    Startup,

    Login,

    Ready,
}

enum class LoginErrorKind {
    InvalidCredentials,
    Network,
    Unknown,
}

data class ChatUiState(
    val phase: Phase = Phase.Startup,
    val isOnline: Boolean = true,
    val username: String = "",
    val password: String = "",
    val isAuthenticating: Boolean = false,
    val loginError: LoginErrorKind? = null,
    val channels: List<String> = emptyList(),
    val isRefreshingChannels: Boolean = false,
    val selectedChannel: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isRefreshingMessages: Boolean = false,
    val messagesError: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val draft: String = "",
    val fullscreenImage: String? = null,
)
