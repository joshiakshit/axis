package com.ash.axis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.data.session.AxisSession

// Governance gate: re-checks access for the active account, then shows the app, a "waiting for approval"
// screen (only on an explicit `pending`), or a brief spinner while the first check is in flight. When
// governance is disabled (no backend) it falls straight through to `content`.
@Composable
internal fun AccessGate(
    activeAdmno: String?,
    content: @Composable () -> Unit,
) {
    val viewModel: AccessViewModel = hiltViewModel()
    val access by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(activeAdmno) { viewModel.refresh() }

    when {
        !access.enabled -> content()
        access.status == AxisSession.STATUS_PENDING -> PendingScreen(onRetry = viewModel::refresh)
        access.status == AxisSession.STATUS_APPROVED -> content()
        access.checking -> CheckingScreen()
        else -> content()
    }
}

@Composable
private fun PendingScreen(onRetry: () -> Unit) {
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
                "Waiting for approval",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Your account is pending approval from the Axis admin. You'll get in as soon as you're approved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) {
                Text("Check again")
            }
        }
    }
}

@Composable
private fun CheckingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
