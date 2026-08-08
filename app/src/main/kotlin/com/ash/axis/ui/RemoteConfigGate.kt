package com.ash.axis.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.BuildConfig
import com.ash.axis.data.config.RemoteConfigRepository
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
                updateUrl = null,
                onRetry = onRetry,
            )

        BuildConfig.VERSION_CODE < config.minSupportedVersionCode ->
            BlockedScreen(
                title = "Update required",
                message = config.message.ifBlank { "A newer version of Axis is required to continue." },
                updateUrl = config.updateUrl.ifBlank { null },
                onRetry = onRetry,
            )

        else -> content()
    }
}

@Composable
private fun BlockedScreen(
    title: String,
    message: String,
    updateUrl: String?,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (updateUrl != null) {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, updateUrl.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                ) {
                    Text("Update")
                }
            }
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
