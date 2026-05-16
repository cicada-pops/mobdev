package io.github.mobdev.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.mobdev.R
import io.github.mobdev.data.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    state: ChatUiState,
    showBackToChannels: Boolean,
    thumbUrl: (String) -> String,
    onOpenImage: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onBackToChannels: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = state.selectedChannel.orEmpty()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (showBackToChannels) {
                        IconButton(onClick = onBackToChannels) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.open_chats),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoadingMessages && state.messages.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    state.messagesError && state.messages.isEmpty() ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.messages_error))
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.retry))
                            }
                        }

                    state.messages.isEmpty() ->
                        Text(
                            text = stringResource(R.string.messages_empty),
                            modifier = Modifier.align(Alignment.Center),
                        )

                    else -> MessageList(
                        state = state,
                        thumbUrl = thumbUrl,
                        onOpenImage = onOpenImage,
                        onLoadOlder = onLoadOlder,
                    )
                }
            }

            MessageInput(
                draft = state.draft,
                isSending = state.isSending,
                onDraftChange = onDraftChange,
                onSend = onSend,
            )
        }
    }
}

@Composable
private fun MessageList(
    state: ChatUiState,
    thumbUrl: (String) -> String,
    onOpenImage: (String) -> Unit,
    onLoadOlder: () -> Unit,
) {
    // Newest-first + reverseLayout keeps the list pinned to the newest message
    // and survives rotation via the saved LazyListState — no network needed.
    val ordered = state.messages.asReversed()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(8.dp),
    ) {
        items(ordered, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                isOwn = message.from == state.username,
                thumbUrl = thumbUrl,
                onOpenImage = onOpenImage,
            )
        }
        if (state.hasMoreOlder) {
            item(key = "load_older") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isLoadingOlder) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onLoadOlder) {
                            Text(stringResource(R.string.load_older))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    thumbUrl: (String) -> String,
    onOpenImage: (String) -> Unit,
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor =
        if (isOwn) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment,
    ) {
        Text(
            text = message.from,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(top = 2.dp),
        ) {
            val image = message.data.image
            val text = message.data.text
            when {
                image != null -> AsyncImage(
                    model = thumbUrl(image.link),
                    contentDescription = stringResource(R.string.image_content_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(220.dp)
                        .clickable { onOpenImage(image.link) }
                        .padding(4.dp),
                )

                text != null -> Text(
                    text = text.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageInput(
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.message_input_hint)) },
            maxLines = 4,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = draft.isNotBlank() && !isSending,
        ) {
            if (isSending) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send),
                )
            }
        }
    }
}
