package com.ash.axis.ui

import com.ash.core.security.AccountState

/** Account state + the actions the switcher/header/settings need, bundled to keep [MainApp] tidy. */
internal data class AccountUiState(
    val account: AccountState,
    val canAddAccount: Boolean,
    val onSwitch: (String) -> Unit,
    val onAdd: () -> Unit,
    val onRemove: (String) -> Unit,
    val onActiveLoggedOut: () -> Unit,
)
