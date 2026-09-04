package com.chronie.homemoney.ui.welcome

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import com.chronie.homemoney.ui.permissions.rememberLocalNetworkPermissionRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import kotlin.time.Duration.Companion.milliseconds
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Welcome / Login screen composable.
 *
 * Renders different UI based on the [WelcomeViewModel.uiState]:
 * - [WelcomeUiState.CheckingLogin] → loading indicator
 * - [WelcomeUiState.NotLoggedIn] → login form with skip + custom-server link
 * - [WelcomeUiState.Loading] → loading with "Logging in..." text
 * - [WelcomeUiState.LoggedIn] → welcome back + "Get Started" button
 * - [WelcomeUiState.Error] → error message with retry form
 *
 * @param context Android context for string resources.
 * @param onGetStartedClick Callback to navigate to the main screen.
 * @param viewModel The WelcomeViewModel (Hilt-injected by default).
 */
@Composable
fun WelcomeScreen(
    context: Context,
    onGetStartedClick: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val username by viewModel.username.collectAsState()

    // Controls the visibility of the server-config dialog. Declared here
    // (not inside LoginForm) so the dialog can be anchored to the outer
    // composition regardless of which uiState branch is mounted.
    var showServerDialog by remember { mutableStateOf(false) }

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

    // Fill the entire window with a Box so we can center the content column
    // horizontally. On narrow phones (<=480dp wide) the inner Column still
    // effectively fills the screen; on tablets / landscape / desktop-sized
    // windows it caps at 480dp, giving balanced side gutters instead of
    // comically wide text fields and buttons.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                // Cap content width for large / landscape screens.
                // widthIn() only clamps when the incoming width exceeds the
                // supplied max, so phones are pixel-identical to before.
                .widthIn(max = 480.dp)
                // Horizontal + vertical padding lives inside the capped box
                // so the usable content width becomes (480 - 2*24) = 432dp
                // on very wide screens — the Material-recommended form width.
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
                        onCustomServerClick = { showServerDialog = true },
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary()
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
                        onCustomServerClick = { showServerDialog = true },
                        context = context
                    )
                }
            }
        }
    }

    // The server-config WindowDialog is mounted side-by-side with the
    // Column so it can overlay the full screen regardless of uiState.
    WelcomeServerConfigDialog(
        show = showServerDialog,
        viewModel = viewModel,
        context = context,
        onDismiss = { showServerDialog = false }
    )
}

/**
 * Login form with username text field, login button, skip option,
 * and an optional "Custom Server" link that opens a separate dialog.
 *
 * @param username Current username input value.
 * @param onUsernameChange Callback when the username text changes.
 * @param onLoginClick Callback when the login button is pressed.
 * @param onSkipLogin Callback when the skip-login button is pressed.
 * @param onCustomServerClick Callback when the "Custom Server" link is
 *   pressed; when null the link is hidden entirely (used e.g. once the
 *   user is past the welcome flow).
 * @param context Android context for string resources.
 */
@Composable
private fun LoginForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onSkipLogin: () -> Unit,
    onCustomServerClick: (() -> Unit)? = null,
    context: Context
) {
    // BoxWithConstraints lets us query the incoming constraints so the
    // two secondary buttons can reflow *responsively* below.
    // IMPORTANT: BoxWithConstraints behaves like a Box – if we put more
    // than one direct child they overlap! Everything must live inside a
    // single Column which becomes the BoxWithConstraints' only child.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Threshold chosen to match the outer 480.dp cap: once we're on a
        // screen/window wide enough that the Column width (after 24.dp
        // side paddings inside the capped box) reaches ~400.dp, we have
        // comfortable room for two ~196.dp buttons with 8.dp gutter.
        val customServerHandler = onCustomServerClick
        val useSideBySide = maxWidth >= 400.dp && customServerHandler != null

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = username,
                onValueChange = onUsernameChange,
                label = context.getString(R.string.auth_username_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = username.isNotBlank(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(context.getString(R.string.auth_login_button))
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (useSideBySide) {
                // ----- Wide layout: Skip | Custom Server side-by-side -----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        text = context.getString(R.string.auth_skip_login),
                        onClick = onSkipLogin,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = context.getString(R.string.welcome_server_config_entry),
                        onClick = customServerHandler,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // ----- Narrow layout: stacked (matches original phone UI) -----
                TextButton(
                    text = context.getString(R.string.auth_skip_login),
                    onClick = onSkipLogin,
                    modifier = Modifier.fillMaxWidth()
                )
                onCustomServerClick?.let { onClick ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        text = context.getString(R.string.welcome_server_config_entry),
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = context.getString(R.string.auth_login_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Server configuration dialog presented from the Welcome screen.
 *
 * Visually a carbon copy of `com.chronie.homemoney.ui.settings.ServerConfigDialog`:
 *   - TextField with URL + hint
 *   - "Test Connection" button with loading indicator while probing
 *   - One-line probe result (Reachable / Failed) under the test button
 *   - Bottom row: "Use Default" TextButton on the left; Close & Save
 *     CircularIconButtons on the right
 *
 * Persisted state lives in [WelcomeViewModel]; the dialog is intentionally
 * dumb – every mutation goes through a viewModel method.
 */
@Composable
private fun WelcomeServerConfigDialog(
    show: Boolean,
    viewModel: WelcomeViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    val input by viewModel.serverUrlInput.collectAsState()
    val testState by viewModel.serverTestState.collectAsState()

    // Android 17 LNP guard: request ACCESS_LOCAL_NETWORK before saving a
    // custom server address (LAN/loopback). On denial we show an explicit
    // hint rather than letting the connection fail with a silent timeout.
    val lanPermissionRequester = rememberLocalNetworkPermissionRequester(
        onDenied = {
            Toast.makeText(context, R.string.lan_permission_denied, Toast.LENGTH_LONG).show()
        }
    )

    WindowDialog(
        show = show,
        title = context.getString(R.string.server_address),
        onDismissRequest = {
            // Clear any stale probe error so reopening the dialog starts
            // from a clean slate – exactly mirrors Settings behaviour.
            viewModel.clearServerTestState()
            onDismiss()
        }
    ) {
        Column(
            // Miuix's WindowDialog already caps its own width on large
            // screens, but we additionally constrain the inner content to
            // 480dp so the text-field + action buttons never stretch into
            // comically-wide proportions on desktops / foldable unfolded.
            // widthIn(max) only narrows, never widens, so phones are unaffected.
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Text(
                text = context.getString(R.string.server_address_hint),
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            TextField(
                value = input,
                onValueChange = viewModel::onServerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = context.getString(R.string.server_address_hint),
                useLabelAsPlaceholder = true,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.testServerConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = testState !is ServerTestUiState.Testing && input.isNotBlank(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                if (testState is ServerTestUiState.Testing) {
                    ExpressiveLoadingIndicator(containerVisible = false)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = context.getString(R.string.server_test_connection))
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val state = testState) {
                is ServerTestUiState.Reachable -> Text(
                    text = context.getString(R.string.server_test_reachable, state.latencyMs),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2
                )
                is ServerTestUiState.Failed -> Text(
                    text = state.message,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2
                )
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = context.getString(R.string.server_reset_default),
                    onClick = {
                        viewModel.resetServerUrl()
                        viewModel.clearServerTestState()
                        onDismiss()
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularIconButton(
                        onClick = {
                            viewModel.clearServerTestState()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = context.getString(android.R.string.cancel),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularIconButton(
                        onClick = {
                            lanPermissionRequester.ensure {
                                if (viewModel.saveServerUrl()) {
                                    viewModel.clearServerTestState()
                                    onDismiss()
                                }
                            }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = context.getString(android.R.string.ok),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
