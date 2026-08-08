package com.ash.axis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.data.session.AxisSession

// Governance gate. Deliberately *load-first*: the app renders immediately and the access check runs in the
// background, so governance never adds to startup time. Only an explicit `pending` verdict swaps in the
// waiting screen; unknown / offline / disabled all fall through (fail-open). The check re-runs on every
// resume, so a revoke (Kick) lands the next time the user foregrounds the app — that's the "instant" part.
@Composable
internal fun AccessGate(
    activeAdmno: String?,
    content: @Composable () -> Unit,
) {
    val viewModel: AccessViewModel = hiltViewModel()
    val access by viewModel.state.collectAsStateWithLifecycle()

    // Re-check when the active account changes (switch) and on every resume (foreground). Both are cheap and
    // off the render path, so neither delays the UI.
    LaunchedEffect(activeAdmno) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    when {
        !access.enabled -> content()
        access.status == AxisSession.STATUS_PENDING -> PendingScreen(onRetry = viewModel::refresh)
        access.status == AxisSession.STATUS_BANNED -> BannedScreen()
        else -> content()
    }
}

@Composable
private fun BannedScreen() {
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
                "Access revoked",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Your access to Axis has been removed by the admin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
