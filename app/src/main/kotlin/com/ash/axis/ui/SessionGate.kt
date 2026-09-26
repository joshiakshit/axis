package com.ash.axis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.ui.auth.LoginScreen
import com.ash.core.storage.PreferencesStore

@Composable
internal fun SessionGate(
    preferencesStore: PreferencesStore,
    qrScanRequest: Int,
    startRoute: String,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val account by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(account.activeAdmno) { viewModel.activate(startRoute) }
    if (!account.isLoggedIn) {
        LoginScreen(onLoginSuccess = viewModel::refresh)
    } else {
        key(account.activeAdmno) {
            AccessGate(activeAdmno = account.activeAdmno, onLogout = viewModel::logout) {
                MainApp(
                    preferencesStore = preferencesStore,
                    qrScanRequest = qrScanRequest,
                    startRoute = startRoute.takeIf { it in tabRoutes } ?: "dashboard",
                    onRouteVisible = viewModel::routeVisible,
                    activeAdmno = requireNotNull(account.activeAdmno),
                    onLogout = viewModel::refresh,
                )
            }
        }
    }
}
