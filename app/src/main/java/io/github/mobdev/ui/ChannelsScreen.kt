package io.github.mobdev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mobdev.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    state: ChatUiState,
    onSelect: (String) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.channels_title)) },
                actions = {
                    if (state.isOnline) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                            )
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.logout_content_description),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!state.isOnline) OfflineBanner()
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.channels.isNotEmpty() -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.channels, key = { it }) { channel ->
                                ChannelRow(
                                    name = channel,
                                    selected = channel == state.selectedChannel,
                                    onClick = { onSelect(channel) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    state.isRefreshingChannels -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                    else -> {
                        Text(
                            text = stringResource(
                                if (state.isOnline) R.string.channels_empty
                                else R.string.channels_empty_offline
                            ),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface
    Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(background)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}
