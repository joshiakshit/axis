package com.ash.axis.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.data.config.RemoteConfig

// A button that turns into a live progress bar while the APK downloads, then a "starting installer" note when
// the system takes over. Reused by the forced-update screen and the soft update dialog.
@Composable
fun UpdateButton(
    url: String,
    label: String,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val update by viewModel.update.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            update.committing ->
                Text(
                    "Starting installer…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

            update.downloading -> {
                if (update.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { update.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "Downloading update…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Button(onClick = { viewModel.install(url) }) { Text(label) }
        }
        update.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

// A dismissible "update available" prompt for the non-forced case (a newer build exists but this one still works).
@Composable
fun UpdateAvailableDialog(
    config: RemoteConfig,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column {
                val version = config.latestVersionName.ifBlank { "a new version" }
                Text(
                    "Axis $version is ready. Update now to get the latest fixes.",
                    textAlign = TextAlign.Start,
                )
            }
        },
        confirmButton = { UpdateButton(url = config.updateUrl, label = "Update") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}
