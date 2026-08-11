package com.ash.axis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.BuildConfig
import com.ash.axis.ui.update.UpdateButton
import com.ash.axis.ui.update.UpdateViewModel

// "Check for updates" card: shows the installed version and, on tap, re-fetches remote config. If a newer build
// is published it turns into the one-tap installer; otherwise it confirms you're up to date.
@Composable
internal fun UpdateSettings(viewModel: UpdateViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val available = viewModel.available(config)

    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Axis v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                checking ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Checking…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                available -> UpdateButton(url = config.updateUrl, label = "Update now")

                checked ->
                    Text(
                        "You're on the latest version.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )

                else -> TextButton(onClick = viewModel::checkForUpdates) { Text("Check for updates") }
            }
        }
    }
}
