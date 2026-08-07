package com.ash.axis.ui

import androidx.lifecycle.ViewModel
import com.ash.core.security.AccountManager
import com.ash.core.security.AccountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        private val accountManager: AccountManager,
    ) : ViewModel() {
        val state: StateFlow<AccountState> = accountManager.state

        fun canAddAccount(): Boolean = accountManager.canAddAccount()

        /** Re-read accounts from storage (after a login adds/activates one). */
        fun refresh() = accountManager.refresh()

        fun switchTo(admno: String) {
            accountManager.switchTo(admno)
        }

        fun remove(admno: String) {
            accountManager.remove(admno)
        }
    }
