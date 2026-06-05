package io.github.mobdev.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mobdev.R

@Composable
fun ChatApp(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.phase) {
        Phase.Startup -> StartupScreen()

        Phase.Login -> LoginScreen(
            state = state,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onSubmit = viewModel::submitLogin,
            onDismissError = viewModel::dismissLoginError,
        )

        Phase.Ready -> ReadyScreen(state, viewModel)
    }
}

@Composable
private fun StartupScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReadyScreen(state: ChatUiState, viewModel: ChatViewModel) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val imageOpen = state.fullscreenImage != null
    val chatOpen = state.selectedChannel != null

    BackHandler(enabled = imageOpen || chatOpen) {
        when {
            imageOpen -> viewModel.closeImage()
            chatOpen -> viewModel.closeChannel()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                ChannelsScreen(
                    state = state,
                    onSelect = viewModel::selectChannel,
                    onLogout = viewModel::logout,
                    onRefresh = viewModel::refreshChannels,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider()
                Box(Modifier.weight(2f).fillMaxSize()) {
                    if (chatOpen) {
                        MessagesPane(state, viewModel, showBackToChannels = false)
                    } else {
                        Text(
                            text = stringResource(R.string.select_chat),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        } else {
            if (chatOpen) {
                MessagesPane(state, viewModel, showBackToChannels = true)
            } else {
                ChannelsScreen(
                    state = state,
                    onSelect = viewModel::selectChannel,
                    onLogout = viewModel::logout,
                    onRefresh = viewModel::refreshChannels,
                )
            }
        }

        state.fullscreenImage?.let { link ->
            FullscreenImage(
                imageUrl = viewModel.imageUrl(link),
                onClose = viewModel::closeImage,
            )
        }
    }
}

@Composable
private fun MessagesPane(
    state: ChatUiState,
    viewModel: ChatViewModel,
    showBackToChannels: Boolean,
) {
    MessagesScreen(
        state = state,
        showBackToChannels = showBackToChannels,
        thumbUrl = viewModel::thumbUrl,
        onOpenImage = viewModel::openImage,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::sendDraft,
        onLoadOlder = viewModel::loadOlderMessages,
        onBackToChannels = viewModel::closeChannel,
        onRetry = viewModel::retryMessages,
    )
}
