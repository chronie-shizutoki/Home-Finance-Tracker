package com.chronie.homemoney.ui.welcome

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R

@Composable
fun WelcomeScreen(
    context: Context,
    onSettingsClick: () -> Unit,
    onGetStartedClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = context.getString(R.string.welcome_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.settings))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onGetStartedClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.getting_started))
        }
    }
}
