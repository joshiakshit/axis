package com.ash.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive view over [TokenManager]'s multi-account storage. UI observes [state] so switching or
 * removing an account recomposes the app; the underlying tokens/accounts live in [TokenManager].
 */
data class AccountState(
    val activeAdmno: String? = null,
    val accounts: List<AccountEntry> = emptyList(),
) {
    val activeAccount: AccountEntry?
        get() = accounts.firstOrNull { it.admno == activeAdmno }

    val isLoggedIn: Boolean
        get() = activeAdmno != null && accounts.isNotEmpty()

    companion object {
        const val MAX_ACCOUNTS = 5
    }
}

@Singleton
class AccountManager
    @Inject
    constructor(
        private val tokenManager: TokenManager,
    ) {
        private val _state = MutableStateFlow(readState())
        val state: StateFlow<AccountState> = _state.asStateFlow()

        private fun readState(): AccountState =
            AccountState(
                activeAdmno = tokenManager.getActiveAdmno(),
                accounts = tokenManager.getAccountList(),
            )

        /** Re-read from storage. Call after a login has added/activated an account. */
        fun refresh() {
            _state.value = readState()
        }

        /** True if another account can be added (under the cap). */
        fun canAddAccount(): Boolean = tokenManager.getAccountList().size < AccountState.MAX_ACCOUNTS

        fun switchTo(admno: String): Boolean {
            val switched = tokenManager.switchTo(admno)
            if (switched) refresh()
            return switched
        }

        /** Remove an account. [TokenManager] reassigns the active account to the next one, if any. */
        fun remove(admno: String) {
            tokenManager.removeAccount(admno)
            refresh()
        }
    }
