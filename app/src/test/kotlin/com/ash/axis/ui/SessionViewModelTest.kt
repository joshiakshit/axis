package com.ash.axis.ui

import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicDestination
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.domain.model.UserInfo
import com.ash.core.security.AccountManager
import com.ash.core.security.AccountState
import com.ash.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    @Test
    fun `saved account selection activates before a route demand and switch deactivates first`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val account = mockk<AccountManager>()
                val auth = mockk<AuthRepository>()
                val preferences = mockk<PreferencesStore>()
                val academic = mockk<AcademicDataCoordinator>(relaxed = true)
                every { account.state } returns MutableStateFlow(AccountState(activeAdmno = "A"))
                every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "Client", academicYear = "2026")
                every { preferences.getString("A_selected_semester_year_id", any()) } returns flowOf("Y1")
                every { preferences.getString("A_selected_semester_class_id", any()) } returns flowOf("C1")
                every { account.switchTo("B") } returns true
                coEvery { academic.activate(any(), any(), any(), any(), any()) } returns Unit
                val viewModel = SessionViewModel(account, auth, preferences, academic)

                viewModel.activate("dashboard")
                runCurrent()
                coVerify(exactly = 1) { academic.activate(any(), any(), any(), AcademicDestination.HOME, null) }
                viewModel.switchTo("B")
                runCurrent()
                coVerifyOrder {
                    academic.deactivate()
                    academic.activate(any(), any(), any(), AcademicDestination.HOME, null)
                    academic.deactivate()
                    account.switchTo("B")
                }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
