package com.ash.axis.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ash.axis.data.session.AdminUser
import com.ash.axis.data.session.AxisSession

@Composable
internal fun AdminSettings(viewModel: AdminViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Users",
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::load) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            state.error?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            if (!state.loading && state.error == null && state.users.isEmpty()) {
                Text(
                    "No users yet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.users.forEach { user ->
                AdminUserRow(
                    user = user,
                    busy = state.busyAdmno == user.admno,
                    onAllow = { viewModel.setStatus(user.admno, allow = true) },
                    onKick = { viewModel.setStatus(user.admno, allow = false) },
                )
            }
        }
    }
}

@Composable
private fun AdminUserRow(
    user: AdminUser,
    busy: Boolean,
    onAllow: () -> Unit,
    onKick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.name.ifBlank { user.admno },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle(user),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(8.dp))
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            user.role == AxisSession.ROLE_ADMIN ->
                Text(
                    "admin",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            user.status == AxisSession.STATUS_PENDING ->
                TextButton(onClick = onAllow) { Text("Allow") }
            else ->
                TextButton(onClick = onKick) {
                    Text("Kick", color = MaterialTheme.colorScheme.error)
                }
        }
    }
}

private fun subtitle(user: AdminUser): String {
    val seen = user.lastSeenAt.take(10)
    val tail = if (seen.isBlank()) "" else " · seen $seen"
    return "${user.admno} · ${user.status}$tail"
}
