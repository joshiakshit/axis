package com.ash.axis.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ash.axis.ui.academics.AcademicsScreen
import com.ash.axis.ui.dashboard.DashboardScreen
import com.ash.axis.ui.grades.GradesScreen
import com.ash.axis.ui.qr.QrScanFlow
import com.ash.axis.ui.qr.QrScanViewModel
import com.ash.axis.ui.settings.SettingsScreen
import com.ash.axis.ui.timetable.TimetableScreen
import com.ash.core.storage.PreferencesStore
import com.ash.core.ui.navigation.AppScaffold
import com.ash.core.ui.navigation.BottomNavItem
import com.ash.core.ui.navigation.CoreNavHost
import com.ash.core.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun MainApp(
    preferencesStore: PreferencesStore,
    qrScanRequest: Int,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val qrViewModel: QrScanViewModel = hiltViewModel()
    val qrState by qrViewModel.state.collectAsStateWithLifecycle()
    var showQrFlow by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val themeModeStr by preferencesStore
        .getString("theme_mode", ThemeMode.DARK.name)
        .collectAsStateWithLifecycle(initialValue = ThemeMode.DARK.name)
    val themeMode = ThemeMode.entries.find { it.name == themeModeStr } ?: ThemeMode.DARK
    val compactNavBar by preferencesStore
        .getBoolean("compact_nav_bar")
        .collectAsStateWithLifecycle(initialValue = false)

    val allNavItems =
        listOf(
            BottomNavItem("Home", Icons.Default.Dashboard, "dashboard"),
            BottomNavItem("Attendance", Icons.AutoMirrored.Filled.FactCheck, "academics"),
            BottomNavItem("Timetable", Icons.Default.EditCalendar, "planner"),
            BottomNavItem("Grades", Icons.Default.School, "grades"),
        )
    val startRoute = "dashboard"

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
    val onThemeToggle: () -> Unit = {
        scope.launch { preferencesStore.putString("theme_mode", themeMode.next().name) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            items = allNavItems,
            currentRoute = currentRoute,
            onNavigate = { route ->
                if (currentRoute == "settings") {
                    navController.popBackStack()
                }
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            showBottomBar = currentRoute != "settings",
            compactNavBar = compactNavBar,
            fabIcon = Icons.Default.QrCodeScanner,
            onFabClick = {
                showQrFlow = true
            },
            topBar = {
                AppHeader(
                    themeMode = themeMode,
                    onThemeToggle = onThemeToggle,
                    onSettingsClick = navigateToSettings,
                )
            },
        ) { innerPadding ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                CoreNavHost(
                    navController = navController,
                    startDestination = startRoute,
                    slideRoutes = setOf("settings", "grades"),
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
        )
    }
}
