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
import androidx.compose.material.icons.filled.School
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
import com.ash.axis.ui.account.AccountSwitcherSheet
import com.ash.axis.ui.dashboard.DashboardScreen
import com.ash.axis.ui.grades.GradesScreen
import com.ash.axis.ui.qr.QrScanFlow
import com.ash.axis.ui.qr.QrScanViewModel
import com.ash.axis.ui.settings.AdminScreen
import com.ash.axis.ui.settings.SettingsScreen
import com.ash.axis.ui.timetable.TimetableScreen
import com.ash.axis.ui.update.UpdateAvailableDialog
import com.ash.axis.ui.update.UpdateViewModel
import com.ash.core.storage.PreferencesStore
import com.ash.core.ui.navigation.AppScaffold
import com.ash.core.ui.navigation.BottomNavItem
import com.ash.core.ui.navigation.CoreNavHost

// Main bottom-nav routes whose last selection is remembered as the app's reopen destination.
private val tabRoutes = setOf("dashboard", "academics", "planner", "grades")

// Full-screen routes pushed on top of the tabs (no bottom bar); they slide in and pop back.
private val fullScreenRoutes = setOf("settings", "admin")

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun MainApp(
    preferencesStore: PreferencesStore,
    qrScanRequest: Int,
    accounts: AccountUiState,
    startRoute: String,
) {
    val navController = rememberNavController()
    val qrViewModel: QrScanViewModel = hiltViewModel()
    val qrState by qrViewModel.state.collectAsStateWithLifecycle()
    var showQrFlow by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    val account = accounts.account
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateConfig by updateViewModel.config.collectAsStateWithLifecycle()
    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    // Dismissal is keyed on the notice text, so a *new* admin notice reappears after an old one was dismissed.
    var dismissedNotice by rememberSaveable { mutableStateOf("") }

    // Remember the last main tab so the app reopens where the user left off.
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
            BottomNavItem("Grades", Icons.Default.School, "grades"),
        )
    LaunchedEffect(qrScanRequest) {
        if (qrScanRequest > 0) {
            showQrFlow = true
        }
    }

    val navigateToSettings: () -> Unit = {
        if (currentRoute == "settings") {
            navController.popBackStack()
        } else {
            navController.navigate("settings") { launchSingleTop = true }
        }
    }
    val navigateToAdmin: () -> Unit = {
        navController.navigate("admin") { launchSingleTop = true }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            items = allNavItems,
            currentRoute = currentRoute,
            onNavigate = { route ->
                if (currentRoute in fullScreenRoutes) {
                    navController.popBackStack()
                }
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            showBottomBar = currentRoute !in fullScreenRoutes,
            fabIcon = Icons.Default.QrCodeScanner,
            onFabClick = {
                showQrFlow = true
            },
            topBar = {
                AppHeader(
                    onSettingsClick = navigateToSettings,
                    accountName = account.activeAccount?.name.orEmpty(),
                    hasMultipleAccounts = account.accounts.size > 1,
                    onAccountClick = { showAccountSwitcher = true },
                )
            },
        ) { innerPadding ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                CoreNavHost(
                    navController = navController,
                    startDestination = startRoute,
                    slideRoutes = setOf("settings", "grades", "admin"),
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
                            "grades" to { GradesScreen(modifier = Modifier.padding(innerPadding)) },
                            "settings" to {
                                SettingsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onLogout = accounts.onActiveLoggedOut,
                                    onOpenAdmin = navigateToAdmin,
                                )
                            },
                            "admin" to {
                                AdminScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onBack = { navController.popBackStack() },
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
        )

        // A newer build exists but this one still works — offer a one-tap update, dismissible for the session.
        if (!updateDismissed && updateViewModel.available(updateConfig)) {
            UpdateAvailableDialog(config = updateConfig, onDismiss = { updateDismissed = true })
        }

        // Admin-set announcement banner (non-blocking), floating just above the bottom nav.
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

        if (showAccountSwitcher) {
            AccountSwitcherSheet(
                account = account,
                canAddAccount = accounts.canAddAccount,
                onSwitch = { admno ->
                    showAccountSwitcher = false
                    if (admno != account.activeAdmno) accounts.onSwitch(admno)
                },
                onAddAccount = {
                    showAccountSwitcher = false
                    accounts.onAdd()
                },
                onRemove = accounts.onRemove,
                onDismiss = { showAccountSwitcher = false },
            )
        }
    }
}
