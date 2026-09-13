package com.ash.axis.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.data.config.RemoteConfig
import com.ash.axis.data.update.UpdateStage
import kotlin.math.roundToInt

// A button that turns into a live progress bar while the APK downloads, then a "starting installer" note when
// the system takes over. Reused by the forced-update screen and the soft update dialog.
@Composable
fun UpdateButton(
    url: String,
    label: String,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val update by viewModel.update.collectAsStateWithLifecycle()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (update.stage) {
            UpdateStage.OPENING_INSTALLER ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Download complete. Opening installer…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            UpdateStage.DOWNLOADING -> {
                if (update.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { update.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    if (update.progress >= 0f) {
                        "Downloading update… ${(update.progress * 100).roundToInt().coerceIn(0, 100)}%"
                    } else {
                        "Downloading update…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            UpdateStage.IDLE ->
                Button(
                    onClick = { viewModel.install(url) },
                    modifier = if (wide) Modifier.fillMaxWidth() else Modifier,
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (update.error == null) label else "Try again")
                }
        }
        update.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// A dismissible "update available" prompt for the non-forced case (a newer build exists but this one still works).
@Composable
fun UpdateAvailableDialog(
    config: RemoteConfig,
    onDismiss: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val update by viewModel.update.collectAsStateWithLifecycle()
    val title =
        if (config.latestVersionName.isBlank()) {
            "A new Axis update is ready"
        } else {
            "Axis ${config.latestVersionName} is ready"
        }
    AlertDialog(
        onDismissRequest = { if (update.stage == UpdateStage.IDLE) onDismiss() },
        icon = { Icon(Icons.Default.SystemUpdateAlt, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    "Install the latest fixes and improvements. You can keep using your account after the update.",
                    textAlign = TextAlign.Start,
                )
                UpdateButton(
                    url = config.updateUrl,
                    label = "Update now",
                    modifier = Modifier.fillMaxWidth(),
                    wide = true,
                    viewModel = viewModel,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            if (update.stage == UpdateStage.IDLE) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        },
    )
}

@Composable
fun UpdateCompletedDialog(
    version: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Axis is up to date") },
        text = { Text("Version $version was installed successfully.", textAlign = TextAlign.Center) },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}
