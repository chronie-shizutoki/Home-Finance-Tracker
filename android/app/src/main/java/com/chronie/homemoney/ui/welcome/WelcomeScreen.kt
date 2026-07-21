package com.chronie.homemoney.ui.welcome

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import kotlin.time.Duration.Companion.milliseconds
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WelcomeScreen(
    context: Context,
    onGetStartedClick: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val username by viewModel.username.collectAsState()

    // Listen for skip login event
    LaunchedEffect(Unit) {
        viewModel.skipLoginEvent.collect {
            onGetStartedClick()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is WelcomeUiState.Error) {
            // Show error message in UI for 3 seconds then clear
            kotlinx.coroutines.delay(3000.milliseconds)
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = MiuixTheme.textStyles.title1,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = context.getString(R.string.welcome_message),
            style = MiuixTheme.textStyles.body1,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(48.dp))

        when (uiState) {
            is WelcomeUiState.CheckingLogin -> {
                ExpressiveLoadingIndicator()
            }
            is WelcomeUiState.NotLoggedIn -> {
                LoginForm(
                    username = username,
                    onUsernameChange = viewModel::onUsernameChange,
                    onLoginClick = viewModel::login,
                    onSkipLogin = { viewModel.skipLogin() },
                    context = context
                )
            }
            is WelcomeUiState.Loading -> {
                ExpressiveLoadingIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = context.getString(R.string.auth_logging_in),
                    style = MiuixTheme.textStyles.body2
                )
            }
            is WelcomeUiState.LoggedIn -> {
                val state = uiState as WelcomeUiState.LoggedIn
                Text(
                    text = context.getString(R.string.auth_welcome_back, state.username),
                    style = MiuixTheme.textStyles.body1,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onGetStartedClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.getting_started))
                }
            }
            is WelcomeUiState.Error -> {
                val errorMessage = (uiState as WelcomeUiState.Error).message
                Text(
                    text = errorMessage,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                LoginForm(
                    username = username,
                    onUsernameChange = viewModel::onUsernameChange,
                    onLoginClick = viewModel::login,
                    onSkipLogin = { viewModel.skipLogin() },
                    context = context
                )
            }
        }
    }
}

@Composable
private fun LoginForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onSkipLogin: () -> Unit,
    context: Context
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(context.getString(R.string.auth_username_label)) },
        placeholder = { Text(context.getString(R.string.auth_username_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onLoginClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = username.isNotBlank()
    ) {
        Text(context.getString(R.string.auth_login_button))
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Skip login button
    TextButton(
        onClick = onSkipLogin,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(context.getString(R.string.auth_skip_login))
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = context.getString(R.string.auth_login_hint),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        textAlign = TextAlign.Center
    )
}
