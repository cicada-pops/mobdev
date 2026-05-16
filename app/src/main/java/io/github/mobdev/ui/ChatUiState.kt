package io.github.mobdev.ui

import io.github.mobdev.data.Message

/** Top-level destination the app is currently showing. */
enum class Phase {
    /** Trying to log in with previously saved credentials. */
    Startup,

    /** The login screen. */
    Login,

    /** Logged in: channels and messages. */
    Ready,
}

/** Login failure shown in a dialog; the UI resolves it to a string resource. */
enum class LoginErrorKind {
    InvalidCredentials,
    Network,
    Unknown,
}

/**
 * The whole UI state in one immutable value. Every field is read-only and the
 * lists are plain [List]s, so nothing here can be mutated in place — the
 * ViewModel only ever emits a new copy.
 */
data class ChatUiState(
    val phase: Phase = Phase.Startup,
    val username: String = "",
    val password: String = "",
    val isAuthenticating: Boolean = false,
    val loginError: LoginErrorKind? = null,
    val channels: List<String> = emptyList(),
    val isLoadingChannels: Boolean = false,
    val channelsError: Boolean = false,
    val selectedChannel: String? = null,
    val messages: List<Message> = emptyList(),
    val isLoadingMessages: Boolean = false,
    val messagesError: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val draft: String = "",
    val isSending: Boolean = false,
    val fullscreenImage: String? = null,
)
