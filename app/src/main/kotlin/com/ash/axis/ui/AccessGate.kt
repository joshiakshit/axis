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
import com.ash.core.security.AccountEntry

// Governance gate. Deliberately *load-first*: the app renders immediately and the access check runs in the
// background, so governance never adds to startup time. Only an explicit `pending`/`banned` verdict blocks —
// and even then, any *other* signed-in account can be resumed, so adding a not-yet-approved account never
// traps the user out of an account that already works. The check re-runs on every resume, so a revoke lands
// the next time the user foregrounds the app.
@Composable
internal fun AccessGate(
    activeAdmno: String?,
    accounts: List<AccountEntry>,
    onSwitch: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val viewModel: AccessViewModel = hiltViewModel()
    val access by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(activeAdmno) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val otherAccounts = accounts.filter { it.admno != activeAdmno }

    when {
        !access.enabled -> content()
        access.status == AxisSession.STATUS_PENDING ->
            BlockedAccountScreen(
                title = "Waiting for approval",
                message =
                    "This account is pending approval from the Axis admin. " +
                        "You'll get in as soon as you're approved.",
                onRetry = viewModel::refresh,
                otherAccounts = otherAccounts,
                onSwitch = onSwitch,
            )

        access.status == AxisSession.STATUS_BANNED ->
            BlockedAccountScreen(
                title = "Access revoked",
                message = "This account's access to Axis has been removed by the admin.",
                onRetry = null,
                otherAccounts = otherAccounts,
                onSwitch = onSwitch,
            )

        else -> content()
    }
}

@Composable
private fun BlockedAccountScreen(
    title: String,
    message: String,
    onRetry: (() -> Unit)?,
    otherAccounts: List<AccountEntry>,
    onSwitch: (String) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            onRetry?.let { retry ->
                TextButton(onClick = retry) { Text("Check again") }
            }
            // Escape hatch: hop back to any other signed-in account that already works.
            otherAccounts.forEach { account ->
                TextButton(onClick = { onSwitch(account.admno) }) {
                    Text("Return as ${account.name.ifBlank { account.admno }}")
                }
            }
        }
    }
}
