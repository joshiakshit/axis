package com.ash.axis.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.BuildConfig
import com.ash.axis.data.session.AdminUser
import com.ash.axis.data.session.AxisSession
import com.ash.axis.data.session.UserAction
import java.time.LocalDate

// Owner-only Admin tools. Action-first (no raw config form): pick a force-update floor from a version list,
// broadcast a notice, kill-switch, and manage users with search/filter + a tap-through detail sheet.
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

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var detailUser by remember { mutableStateOf<AdminUser?>(null) }

    val filtered =
        state.users.filter { u ->
            (filter == "all" || u.status == filter) &&
                (query.isBlank() || u.name.contains(query, true) || u.admno.contains(query, true))
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

        item { SectionLabel("FORCE UPDATE") }
        item { ForceUpdateCard(state = state, onSet = viewModel::setMinVersion) }

        item { SectionLabel("BROADCAST") }
        item { BroadcastCard(state = state, onSend = viewModel::setNotice) }

        item { SectionLabel("EMERGENCY") }
        item { KillSwitchCard(state = state, onToggle = viewModel::setKillSwitch) }

        item { SectionLabel("USAGE") }
        item { UsageCard(state) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("USERS · ${state.users.size}")
                Spacer(Modifier.weight(1f))
                if (pendingCount > 0) {
                    TextButton(onClick = viewModel::approveAll) { Text("Approve all ($pendingCount)") }
                }
            }
        }
        item { UserSearchBar(query, { query = it }, filter, { filter = it }) }
        state.error?.let { err -> item { Text(err, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) } }
        if (!state.loading && filtered.isEmpty()) {
            item {
                SettingsCard {
                    Text("No matching users.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(filtered, key = { it.admno }) { user ->
            AdminUserRow(user = user, busy = state.busyAdmno == user.admno, onClick = { detailUser = user })
        }

        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }

    detailUser?.let { user ->
        // Reflect live status changes (allow/kick/ban) back into the open sheet.
        val fresh = state.users.firstOrNull { it.admno == user.admno } ?: user
        UserDetailDialog(
            user = fresh,
            busy = state.busyAdmno == fresh.admno,
            onAction = { action -> viewModel.act(fresh.admno, action) },
            onDismiss = { detailUser = null },
        )
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
    val since = LocalDate.now().minusDays(WEEK).toString()
    SettingsCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Users", (h?.users ?: users.size).toString())
            Stat("Pending", (h?.pending ?: users.count { it.status == AxisSession.STATUS_PENDING }).toString())
            Stat("Approved", (h?.approved ?: users.count { it.status == AxisSession.STATUS_APPROVED }).toString())
            Stat("Banned", (h?.banned ?: users.count { it.status == AxisSession.STATUS_BANNED }).toString())
            Stat("Active 7d", users.count { it.lastSeenAt.take(10) >= since }.toString())
        }
    }
}

// Pick the force-update floor from a scrollable list of real versions (latest published + whatever users run),
// newest first — no version numbers to remember. Selecting one publishes it immediately.
@Composable
private fun ForceUpdateCard(
    state: AdminUiState,
    onSet: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = versionOptions(state)
    val currentMin = state.config?.minSupportedVersionCode ?: 0
    val currentLabel = options.firstOrNull { it.first == currentMin }?.let(::versionLabel) ?: "build $currentMin"

    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Minimum version", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(currentLabel, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    // Cap the height to ~3 rows so a long version list scrolls inside the menu.
                    modifier = Modifier.heightIn(max = 168.dp),
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(versionLabel(option)) },
                            trailingIcon = {
                                if (option.first == currentMin) Icon(Icons.Filled.Check, contentDescription = "current")
                            },
                            onClick = {
                                expanded = false
                                if (option.first != currentMin) onSet(option.first)
                            },
                        )
                    }
                }
            }
            Text(
                "Anyone on a build below this is blocked until they update.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BroadcastCard(
    state: AdminUiState,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.config) {
        if (state.config != null && !seeded) {
            text = state.config?.notice.orEmpty()
            seeded = true
        }
    }
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Message shown to everyone (blank = none)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSend(text) }, enabled = !state.savingConfig) { Text("Send") }
                TextButton(onClick = {
                    text = ""
                    onSend("")
                }, enabled = !state.savingConfig) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun KillSwitchCard(
    state: AdminUiState,
    onToggle: (Boolean, String) -> Unit,
) {
    var message by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.config) {
        if (state.config != null && !seeded) {
            message = state.config?.message.orEmpty()
            seeded = true
        }
    }
    val on = state.config?.killSwitch == true
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleRow("Kill switch — block the app for everyone", on) { onToggle(it, message) }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Reason shown while blocked") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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
        }
    }
}

@Composable
private fun UserSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Search name or admno") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all", AxisSession.STATUS_PENDING, AxisSession.STATUS_APPROVED, AxisSession.STATUS_BANNED)
                .forEach { f ->
                    FilterChip(selected = filter == f, onClick = { onFilter(f) }, label = { Text(f) })
                }
        }
    }
}

@Composable
private fun AdminUserRow(
    user: AdminUser,
    busy: Boolean,
    onClick: () -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "details") { onClick() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name.ifBlank { user.admno },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(userMeta(user), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                busy -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                user.role == AxisSession.ROLE_ADMIN ->
                    Text("admin", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                else -> StatusPill(user.status)
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color =
        when (status) {
            AxisSession.STATUS_APPROVED -> MaterialTheme.colorScheme.primary
            AxisSession.STATUS_BANNED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(status, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
}

@Composable
private fun UserDetailDialog(
    user: AdminUser,
    busy: Boolean,
    onAction: (UserAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.name.ifBlank { user.admno }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyValueRow("Admno", user.admno)
                KeyValueRow("Status", user.status)
                if (user.role == AxisSession.ROLE_ADMIN) KeyValueRow("Role", "admin")
                if (user.email.isNotBlank()) KeyValueRow("Email", user.email)
                if (user.deviceModel.isNotBlank()) KeyValueRow("Device", user.deviceModel)
                if (user.androidSdk > 0) KeyValueRow("Android SDK", user.androidSdk.toString())
                if (user.appVersionName.isNotBlank()) KeyValueRow("App version", "v${user.appVersionName} (${user.appVersionCode})")
                KeyValueRow("Launches", user.sessionCount.toString())
                if (user.firstSeenAt.isNotBlank()) KeyValueRow("Joined", user.firstSeenAt.take(10))
                if (user.lastSeenAt.isNotBlank()) KeyValueRow("Last seen", user.lastSeenAt.take(10))
            }
        },
        confirmButton = {
            if (user.role != AxisSession.ROLE_ADMIN && !busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (user.status != AxisSession.STATUS_APPROVED) {
                        TextButton(onClick = { onAction(UserAction.ALLOW) }) { Text("Allow") }
                    }
                    if (user.status == AxisSession.STATUS_APPROVED) {
                        TextButton(onClick = { onAction(UserAction.KICK) }) { Text("Kick") }
                    }
                    if (user.status != AxisSession.STATUS_BANNED) {
                        TextButton(onClick = { onAction(UserAction.BAN) }) {
                            Text("Ban", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
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

// Build the version dropdown list: latest published + this build + everything users are running, newest first.
private fun versionOptions(state: AdminUiState): List<Pair<Int, String>> {
    val map = linkedMapOf<Int, String>()

    fun add(
        code: Int,
        name: String,
    ) {
        if (code <= 0) return
        if (map[code].isNullOrBlank()) map[code] = name
    }
    state.config?.let {
        add(it.latestVersionCode, it.latestVersionName)
        add(it.minSupportedVersionCode, "")
    }
    add(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
    state.users.forEach { add(it.appVersionCode, it.appVersionName) }
    return map.entries.sortedByDescending { it.key }.map { it.key to it.value }
}

private fun versionLabel(option: Pair<Int, String>): String =
    if (option.second.isBlank()) "build ${option.first}" else "v${option.second} (${option.first})"

private fun userMeta(user: AdminUser): String {
    val parts = mutableListOf(user.status)
    if (user.appVersionName.isNotBlank()) parts += "v${user.appVersionName}"
    if (user.deviceModel.isNotBlank()) parts += user.deviceModel
    if (user.lastSeenAt.isNotBlank()) parts += "seen ${user.lastSeenAt.take(10)}"
    return parts.joinToString(" · ")
}

private const val WEEK = 7L
