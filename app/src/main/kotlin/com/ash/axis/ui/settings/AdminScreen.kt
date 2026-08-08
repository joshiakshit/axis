package com.ash.axis.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.BuildConfig
import com.ash.axis.data.session.AdminUser
import com.ash.axis.data.session.AxisSession
import com.ash.axis.data.session.ConfigPatch
import com.ash.axis.data.session.UserAction
import java.time.LocalDate

// The dedicated Admin tools page (owner-only): an at-a-glance overview + usage metrics, remote app controls
// (force-update floor, latest build, kill-switch, notice, auto-approve — pushed live to every client), and the
// governed user list with Allow / Kick / Ban.
@Suppress("LongMethod")
@Composable
fun AdminScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.configMessage) {
        state.configMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeConfigMessage()
        }
    }

    val pendingCount = state.users.count { it.status == AxisSession.STATUS_PENDING }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(6.dp)) }
        item { AdminTopBar(onBack = onBack, loading = state.loading, onRefresh = viewModel::load) }

        item { SectionLabel("OVERVIEW") }
        item { AdminOverview(state) }

        item { SectionLabel("USAGE") }
        item { UsageCard(state) }

        item { SectionLabel("APP CONTROL") }
        item { AppControlCard(state = state, onPublish = viewModel::saveConfig) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("USERS")
                Spacer(Modifier.weight(1f))
                if (pendingCount > 0) {
                    TextButton(onClick = viewModel::approveAll) { Text("Approve all ($pendingCount)") }
                }
            }
        }
        state.error?.let { err -> item { Text(err, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) } }
        if (!state.loading && state.users.isEmpty()) {
            item {
                SettingsCard {
                    Text("No users yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(state.users, key = { it.admno }) { user ->
            AdminUserCard(
                user = user,
                busy = state.busyAdmno == user.admno,
                onAction = { action -> viewModel.act(user.admno, action) },
            )
        }

        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

@Composable
private fun AdminTopBar(
    onBack: () -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Admin tools", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
    }
}

@Composable
private fun AdminOverview(state: AdminUiState) {
    val users = state.users
    val h = state.health
    val total = h?.users ?: users.size
    val pending = h?.pending ?: users.count { it.status == AxisSession.STATUS_PENDING }
    val approved = h?.approved ?: users.count { it.status == AxisSession.STATUS_APPROVED }
    val banned = h?.banned ?: users.count { it.status == AxisSession.STATUS_BANNED }
    val since = LocalDate.now().minusDays(WEEK).toString()
    val active = users.count { it.lastSeenAt.take(10) >= since }

    SettingsCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Users", total.toString())
            Stat("Pending", pending.toString())
            Stat("Approved", approved.toString())
            Stat("Banned", banned.toString())
            Stat("Active 7d", active.toString())
        }
    }
}

// Event counters (from the backend) + a version-adoption histogram (from the user list) to guide the
// force-update decision.
@Composable
private fun UsageCard(state: AdminUiState) {
    val metrics = state.health?.metrics.orEmpty()
    val versions =
        state.users
            .filter { it.appVersionName.isNotBlank() }
            .groupingBy { it.appVersionName }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Events", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (metrics.isEmpty()) {
                Text("No events yet.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                metrics.toList().sortedBy { it.first }.forEach { (name, value) -> KeyValueRow(name, value.toString()) }
            }
            Spacer(Modifier.height(6.dp))
            Text("Version adoption", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (versions.isEmpty()) {
                Text("No data yet.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                versions.forEach { (name, count) -> KeyValueRow("v$name", count.toString()) }
            }
            state.health?.let {
                Spacer(Modifier.height(6.dp))
                KeyValueRow("APK uploaded", if (it.apkUploaded) "yes" else "no")
            }
        }
    }
}

@Composable
private fun KeyValueRow(
    key: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Suppress("LongMethod")
@Composable
private fun AppControlCard(
    state: AdminUiState,
    onPublish: (ConfigPatch) -> Unit,
) {
    val config = state.config
    var minCode by rememberSaveable { mutableStateOf("") }
    var latestCode by rememberSaveable { mutableStateOf("") }
    var latestName by rememberSaveable { mutableStateOf("") }
    var updateUrl by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf("") }
    var autoApprovePrefix by rememberSaveable { mutableStateOf("") }
    var killSwitch by rememberSaveable { mutableStateOf(false) }
    var seeded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(config) {
        if (config != null && !seeded) {
            minCode = config.minSupportedVersionCode.toString()
            latestCode = config.latestVersionCode.toString()
            latestName = config.latestVersionName
            updateUrl = config.updateUrl
            message = config.message
            notice = config.notice
            autoApprovePrefix = config.autoApprovePrefix
            killSwitch = config.killSwitch
            seeded = true
        }
    }

    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "This build: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (config == null) {
                Text("Loading config…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                NumberField("Min version code (force-update floor)", minCode) { minCode = it }
                NumberField("Latest version code", latestCode) { latestCode = it }
                TextField("Latest version name", latestName) { latestName = it }
                TextField("Update URL (.apk)", updateUrl) { updateUrl = it }
                TextField("Auto-approve admno prefix (e.g. 024GUSCSE)", autoApprovePrefix) { autoApprovePrefix = it }
                TextField("Notice banner (blank = none)", notice) { notice = it }
                TextField("Block message (kill-switch / update screen)", message) { message = it }
                ToggleRow("Kill switch (block the app)", killSwitch) { killSwitch = it }
                Button(
                    onClick = {
                        onPublish(
                            ConfigPatch(
                                minSupportedVersionCode = minCode.toIntOrNull(),
                                latestVersionCode = latestCode.toIntOrNull(),
                                latestVersionName = latestName,
                                updateUrl = updateUrl,
                                killSwitch = killSwitch,
                                message = message,
                                notice = notice,
                                autoApprovePrefix = autoApprovePrefix,
                            ),
                        )
                    },
                    enabled = !state.savingConfig,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.savingConfig) "Publishing…" else "Publish to all users")
                }
            }
        }
    }
}

@Composable
private fun TextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AdminUserCard(
    user: AdminUser,
    busy: Boolean,
    onAction: (UserAction) -> Unit,
) {
    SettingsCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name.ifBlank { user.admno },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(user.admno, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(userMeta(user), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            UserActions(user = user, busy = busy, onAction = onAction)
        }
    }
}

@Composable
private fun UserActions(
    user: AdminUser,
    busy: Boolean,
    onAction: (UserAction) -> Unit,
) {
    when {
        busy -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        user.role == AxisSession.ROLE_ADMIN ->
            Text("admin", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        user.status == AxisSession.STATUS_BANNED ->
            OutlinedButton(onClick = { onAction(UserAction.ALLOW) }) { Text("Unban") }
        user.status == AxisSession.STATUS_PENDING ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { onAction(UserAction.ALLOW) }) { Text("Allow") }
                TextButton(onClick = { onAction(UserAction.BAN) }) { Text("Ban", color = MaterialTheme.colorScheme.error) }
            }
        else ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { onAction(UserAction.KICK) }) { Text("Kick") }
                TextButton(onClick = { onAction(UserAction.BAN) }) { Text("Ban", color = MaterialTheme.colorScheme.error) }
            }
    }
}

private fun userMeta(user: AdminUser): String {
    val parts = mutableListOf(user.status)
    if (user.lastSeenAt.isNotBlank()) parts += "seen ${user.lastSeenAt.take(10)}"
    if (user.appVersionName.isNotBlank()) parts += "v${user.appVersionName}"
    if (user.deviceModel.isNotBlank()) parts += user.deviceModel
    if (user.sessionCount > 0) parts += "${user.sessionCount}×"
    return parts.joinToString(" · ")
}

private const val WEEK = 7L
