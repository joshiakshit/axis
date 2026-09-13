package com.ash.axis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.BuildConfig
import com.ash.axis.data.config.RemoteConfigRepository
import com.ash.axis.ui.update.UpdateButton
import kotlinx.coroutines.launch

// The backend's safety valve. Reacts to remote config: a kill-switch or a version floor above this build
// blocks the app with a message; otherwise the real content shows. When remote config is disabled/absent
// the config stays at its defaults, so this always falls through to `content`.
@Composable
fun RemoteConfigGate(
    remoteConfig: RemoteConfigRepository,
    content: @Composable () -> Unit,
) {
    val config by remoteConfig.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val onRetry: () -> Unit = { scope.launch { remoteConfig.refresh() } }

    when {
        config.killSwitch ->
            BlockedScreen(
                title = "Axis is paused",
                message = config.message.ifBlank { "Axis is temporarily unavailable. Please try again shortly." },
                onRetry = onRetry,
            )

        BuildConfig.VERSION_CODE < config.minSupportedVersionCode ->
            UpdateRequiredScreen(
                message = config.message.ifBlank { "A newer version of Axis is required to continue." },
                updateUrl = config.updateUrl,
                versionName = config.latestVersionName,
                onRetry = onRetry,
            )

        else -> content()
    }
}

@Composable
private fun UpdateRequiredScreen(
    message: String,
    updateUrl: String,
    versionName: String,
    onRetry: () -> Unit,
) {
    GateScaffold(title = "Update required", message = message) {
        if (versionName.isNotBlank()) {
            Text(
                "Axis $versionName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (updateUrl.isNotBlank()) {
            UpdateButton(
                url = updateUrl,
                label = "Download and update",
                modifier = Modifier.fillMaxWidth(),
                wide = true,
            )
        }
        TextButton(onClick = onRetry) { Text("Check again") }
    }
}

@Composable
private fun BlockedScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    GateScaffold(title = title, message = message) {
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun GateScaffold(
    title: String,
    message: String,
    actions: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                actions()
            }
        }
    }
}
