package com.ash.axis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor

private val navRouteOptions =
    listOf(
        "dashboard" to "Dashboard",
        "attendance" to "Attendance",
        "timetable" to "Timetable",
        "planner" to "Planner",
    )

@Composable
internal fun HomePagePicker(
    defaultPage: String,
    onSelect: (String) -> Unit,
) {
    SettingsCard {
        Text("Home Page", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            navRouteOptions.forEach { (route, label) ->
                val selected = defaultPage == route
                Surface(
                    modifier = Modifier.weight(1f),
                    selected = selected,
                    onClick = { onSelect(route) },
                    shape = AppShapes.small,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            cardColor()
                        },
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SecuritySettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var showDeviceDialog by remember { mutableStateOf(false) }

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

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Device ID", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    state.deviceId.ifBlank { "—" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { showDeviceDialog = true }) { Text("Edit") }
        }
    }

    if (showDeviceDialog) {
        DeviceIdDialog(
            current = state.deviceId,
            onDismiss = { showDeviceDialog = false },
            onSave = {
                viewModel.setDeviceId(it)
                showDeviceDialog = false
            },
        )
    }
}

@Composable
private fun DeviceIdDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device ID") },
        text = {
            Column {
                Text(
                    "Your account is bound to one device at a time. To stay signed in alongside the official " +
                        "iCloudEMS app, paste the device ID it uses. Changing this takes effect on your next login.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
