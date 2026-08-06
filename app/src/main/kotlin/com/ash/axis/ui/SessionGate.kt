package com.ash.axis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ash.axis.ui.auth.LoginScreen
import com.ash.core.storage.PreferencesStore

@Composable
internal fun SessionGate(
    initiallyLoggedIn: Boolean,
    preferencesStore: PreferencesStore,
    qrScanRequest: Int,
) {
    var loggedIn by rememberSaveable { mutableStateOf(initiallyLoggedIn) }

    if (loggedIn) {
        MainApp(
            preferencesStore = preferencesStore,
            qrScanRequest = qrScanRequest,
            onLogout = { loggedIn = false },
        )
    } else {
        LoginScreen(onLoginSuccess = { loggedIn = true })
    }
}
