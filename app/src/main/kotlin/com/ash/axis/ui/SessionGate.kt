package com.ash.axis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var addingAccount by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(account.activeAdmno) { viewModel.activate(startRoute) }

    when {
        !account.isLoggedIn -> {
            LoginScreen(onLoginSuccess = { viewModel.refresh() })
        }
        addingAccount -> {
            BackHandler { addingAccount = false }
            LoginScreen(
                onLoginSuccess = {
                    addingAccount = false
                    viewModel.refresh()
                },
            )
        }
        // Recreate screens and ViewModels when the active account changes.
        else -> {
            AccessGate(
                activeAdmno = account.activeAdmno,
                accounts = account.accounts,
                onSwitch = viewModel::switchTo,
            ) {
                key(account.activeAdmno) {
                    MainApp(
                        preferencesStore = preferencesStore,
                        qrScanRequest = qrScanRequest,
                        startRoute = startRoute,
                        onRouteVisible = viewModel::routeVisible,
                        accounts =
                            AccountUiState(
                                account = account,
                                canAddAccount = viewModel.canAddAccount(),
                                onSwitch = viewModel::switchTo,
                                onAdd = { addingAccount = true },
                                onRemove = viewModel::remove,
                                onActiveLoggedOut = viewModel::refresh,
                            ),
                    )
                }
            }
        }
    }
}
