package com.ash.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AccountState(
    val activeAdmno: String? = null,
) {
    val isLoggedIn: Boolean get() = activeAdmno != null
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
                activeAdmno = tokenManager.getActiveAdmno().takeIf { tokenManager.hasTokens() },
            )

        fun refresh() {
            _state.value = readState()
        }
    }
