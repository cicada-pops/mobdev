package io.github.mobdev.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mobdev.R

@Composable
fun LoginScreen(
    state: ChatUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
) {
    val passwordFocus = remember { FocusRequester() }
    val canEdit = !state.isAuthenticating

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            singleLine = true,
            enabled = canEdit,
            label = { Text(stringResource(R.string.login_username_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            singleLine = true,
            enabled = canEdit,
            label = { Text(stringResource(R.string.login_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .focusRequester(passwordFocus),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = canEdit,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        ) {
            if (state.isAuthenticating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.login_in_progress))
            } else {
                Text(stringResource(R.string.login_button))
            }
        }
    }

    state.loginError?.let { kind ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(stringResource(kind.messageRes())) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
        )
    }
}

private fun LoginErrorKind.messageRes(): Int = when (this) {
    LoginErrorKind.InvalidCredentials -> R.string.error_invalid_credentials
    LoginErrorKind.Network -> R.string.error_network
    LoginErrorKind.Unknown -> R.string.error_unknown
}
