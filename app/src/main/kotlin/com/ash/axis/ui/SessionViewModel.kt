package com.ash.axis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicDestination
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.core.security.AccountManager
import com.ash.core.security.AccountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        private val accountManager: AccountManager,
        private val authRepository: AuthRepository,
        private val academic: AcademicDataCoordinator,
    ) : ViewModel() {
        val state: StateFlow<AccountState> = accountManager.state
        private var activation: Job? = null

        fun activate(route: String) {
            activation?.cancel()
            activation =
                viewModelScope.launch {
                    academic.deactivate()
                    val account = state.value.activeAdmno ?: return@launch
                    val user = authRepository.getUserInfo()?.takeIf { it.admno == account } ?: return@launch
                    val context = StudentRequestContext(user.admno, user.brId, user.clientId, user.academicYear)
                    coroutineScope {
                        launch { academic.activate(context, null, currentWeek(), destination(route)) }
                        launch {
                            academic.activeContext.first {
                                it?.admno == context.admno && it.brId == context.brId && it.clientId == context.clientId
                            }
                            academic.discoverSemester()
                        }
                    }
                }
        }

        fun routeVisible(route: String) {
            viewModelScope.launch {
                when (route) {
                    "dashboard" -> academic.homeVisible()
                    "academics" -> academic.attendanceVisible()
                    "planner" -> academic.timetableVisible(currentWeek())
                    else -> academic.otherVisible()
                }
            }
        }

        private fun destination(route: String) =
            when (route) {
                "dashboard" -> AcademicDestination.HOME
                "academics" -> AcademicDestination.ATTENDANCE
                "planner" -> AcademicDestination.TIMETABLE
                else -> AcademicDestination.OTHER
            }

        private fun currentWeek() = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

        fun refresh() = accountManager.refresh()

        fun logout() {
            activation?.cancel()
            viewModelScope.launch {
                academic.deactivate()
                academic.clearAcademicCache()
                authRepository.logout()
                accountManager.refresh()
            }
        }
    }
