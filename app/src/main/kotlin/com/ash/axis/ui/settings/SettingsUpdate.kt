package com.ash.axis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.BuildConfig
import com.ash.axis.ui.update.UpdateButton
import com.ash.axis.ui.update.UpdateViewModel

@Composable
internal fun UpdateSettings(viewModel: UpdateViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val error by viewModel.checkError.collectAsStateWithLifecycle()
    val available = viewModel.available(config)

    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Update", style = MaterialTheme.typography.titleSmall)
                    Text("Installed: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = viewModel::checkForUpdates, enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Check for updates")
                    }
                }
            }
            when {
                checking -> Text("Checking for updates…", style = MaterialTheme.typography.bodySmall)
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                checked && !available -> Text("You're on the latest version.", color = MaterialTheme.colorScheme.primary)
            }
            if (available) {
                UpdateButton(url = config.updateUrl, label = "Update now", modifier = Modifier.fillMaxWidth(), wide = true)
            }
        }
    }
}
