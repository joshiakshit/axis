package com.ash.axis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun SecuritySettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Clear Cache", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = viewModel::clearCache, enabled = !state.isClearing) {
                Text(if (state.isClearing) "Clearing..." else "Clear", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
