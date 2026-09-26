package com.ash.axis.ui

import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicDestination
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import com.ash.core.security.AccountManager
import com.ash.core.security.AccountState
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    @Test
    fun `startup discovers latest semester instead of restoring a manual selection`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val account = mockk<AccountManager>()
                val auth = mockk<AuthRepository>()
                val academic = mockk<AcademicDataCoordinator>(relaxed = true)
                val active = MutableStateFlow<StudentRequestContext?>(null)
                every { account.state } returns MutableStateFlow(AccountState(activeAdmno = "A"))
                every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "Client", academicYear = "2026")
                every { academic.activeContext } returns active
                coEvery { academic.activate(any(), any(), any(), any(), any()) } coAnswers { active.value = firstArg() }
                val viewModel = SessionViewModel(account, auth, academic)
                viewModel.activate("dashboard")
                runCurrent()
                coVerifyOrder {
                    academic.deactivate()
                    academic.activate(any(), null, any(), AcademicDestination.HOME, null)
                    academic.discoverSemester()
                }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
