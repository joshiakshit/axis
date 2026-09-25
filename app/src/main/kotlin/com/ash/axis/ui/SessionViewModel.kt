package com.ash.axis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicDestination
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.core.security.AccountManager
import com.ash.core.security.AccountState
import com.ash.core.storage.PreferencesStore
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
        private val preferences: PreferencesStore,
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
                    val year = preferences.getString("${account}_$SELECTED_SEMESTER_YEAR_KEY").first()
                    val classId = preferences.getString("${account}_$SELECTED_SEMESTER_CLASS_KEY").first()
                    if (state.value.activeAdmno != account) return@launch
                    val selected = if (year.isNotBlank() && classId.isNotBlank()) SemesterOption(year, classId, "") else null
                    val context = StudentRequestContext(user.admno, user.brId, user.clientId, user.academicYear)
                    coroutineScope {
                        launch { academic.activate(context, selected, currentWeek(), destination(route)) }
                        if (selected == null || route == "academics") {
                            launch {
                                academic.activeContext.first {
                                    it?.admno == context.admno && it.brId == context.brId && it.clientId == context.clientId
                                }
                                academic.discoverSemester(year, classId)
                            }
                        }
                    }
                }
        }

        fun routeVisible(route: String) {
            viewModelScope.launch {
                when (route) {
                    "dashboard" -> academic.homeVisible()
                    "academics" -> {
                        academic.attendanceVisible()
                        val selection = academic.selectedSemester.value
                        val option = selection.option
                        if (option?.label?.isBlank() == true && !selection.loading && selection.error == null) {
                            academic.discoverSemester(option.yearId, option.classId)
                        }
                    }
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

        fun canAddAccount(): Boolean = accountManager.canAddAccount()

        fun refresh() = accountManager.refresh()

        fun switchTo(admno: String) {
            activation?.cancel()
            viewModelScope.launch {
                academic.deactivate()
                accountManager.switchTo(admno)
            }
        }

        fun remove(admno: String) {
            if (admno == state.value.activeAdmno) {
                activation?.cancel()
                viewModelScope.launch {
                    academic.deactivate()
                    accountManager.remove(admno)
                }
            } else {
                accountManager.remove(admno)
            }
        }
    }
