package com.ash.axis.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ash.axis.ui.academics.AcademicsScreen
import com.ash.axis.ui.dashboard.DashboardScreen
import com.ash.axis.ui.qr.QrScanFlow
import com.ash.axis.ui.qr.QrScanViewModel
import com.ash.axis.ui.settings.SettingsScreen
import com.ash.axis.ui.timetable.TimetableScreen
import com.ash.axis.ui.update.UpdateAvailableDialog
import com.ash.axis.ui.update.UpdateCompletedDialog
import com.ash.axis.ui.update.UpdateViewModel
import com.ash.core.storage.PreferencesStore
import com.ash.core.ui.navigation.AppScaffold
import com.ash.core.ui.navigation.BottomNavItem
import com.ash.core.ui.navigation.CoreNavHost

internal val tabRoutes = setOf("dashboard", "academics", "planner", "settings")

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun MainApp(
    preferencesStore: PreferencesStore,
    qrScanRequest: Int,
    activeAdmno: String,
    onLogout: () -> Unit,
    startRoute: String,
    onRouteVisible: (String) -> Unit,
) {
    val navController = rememberNavController()
    val qrViewModel: QrScanViewModel = hiltViewModel(key = "qr_$activeAdmno")
    val qrState by qrViewModel.state.collectAsStateWithLifecycle()
    var showQrFlow by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) { currentRoute?.let(onRouteVisible) }

    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateConfig by updateViewModel.config.collectAsStateWithLifecycle()
    val completedUpdate by updateViewModel.completedVersion.collectAsStateWithLifecycle()
    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    // A changed notice must appear even if the previous notice was dismissed.
    var dismissedNotice by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(currentRoute) {
        val route = currentRoute
        if (route != null && route in tabRoutes) {
            preferencesStore.putString("last_route", route)
        }
    }

    val allNavItems =
        listOf(
            BottomNavItem("Home", Icons.Default.Dashboard, "dashboard"),
            BottomNavItem("Attendance", Icons.AutoMirrored.Filled.FactCheck, "academics"),
            BottomNavItem("Timetable", Icons.Default.EditCalendar, "planner"),
            BottomNavItem("Settings", Icons.Default.Settings, "settings"),
        )
    LaunchedEffect(qrScanRequest) {
        if (qrScanRequest > 0) {
            showQrFlow = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            items = allNavItems,
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            fabIcon = Icons.Default.QrCodeScanner,
            onFabClick = {
                showQrFlow = true
            },
            topBar = { AppHeader() },
        ) { innerPadding ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                CoreNavHost(
                    navController = navController,
                    startDestination = startRoute,
                    routes =
                        mapOf(
                            "dashboard" to {
                                DashboardScreen(
                                    modifier = Modifier.padding(innerPadding),
                                )
                            },
                            "academics" to {
                                AcademicsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                )
                            },
                            "planner" to { TimetableScreen(Modifier.padding(innerPadding)) },
                            "settings" to {
                                SettingsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onLogout = onLogout,
                                )
                            },
                        ),
                )
            }
        }

        QrScanFlow(
            visible = showQrFlow,
            isSubmitting = qrState.isSubmitting,
            message = qrState.message,
            success = qrState.success,
            onSubmit = qrViewModel::submitQrScan,
            onShowMessage = qrViewModel::showMessage,
            onClearMessage = qrViewModel::clearMessage,
            onDismiss = { showQrFlow = false },
            diagnostics = qrViewModel.diagnostics,
        )

        if (completedUpdate == null && !updateDismissed && updateViewModel.available(updateConfig)) {
            UpdateAvailableDialog(
                config = updateConfig,
                onDismiss = { updateDismissed = true },
                viewModel = updateViewModel,
            )
        }

        completedUpdate?.let { version ->
            UpdateCompletedDialog(version = version, onDismiss = updateViewModel::dismissCompletedUpdate)
        }

        val notice = updateConfig.notice
        if (notice.isNotBlank() && notice != dismissedNotice) {
            NoticeBanner(
                text = notice,
                onDismiss = { dismissedNotice = notice },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 96.dp),
            )
        }
    }
}
