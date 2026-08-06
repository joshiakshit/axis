package com.ash.axis.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.SemesterOption
import com.ash.core.storage.PreferencesStore
import com.ash.core.ui.theme.ColorProfiles
import com.ash.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "",
    val admno: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val colorProfile: String = ColorProfiles.Default.name,
    val compactNavBar: Boolean = false,
    val defaultPage: String = "dashboard",
    val threshold: Int = 75,
    val semesterEndDate: String = "",
    val selectedSemester: SemesterOption? = null,
    val semesterOptions: List<SemesterOption> = emptyList(),
    val semesterError: String? = null,
    val combinedAttendance: Boolean = false,
    val isClearing: Boolean = false,
    val deviceId: String = "",
) {
    companion object {
        val validPages = listOf("dashboard", "attendance", "timetable", "planner")
    }
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferencesStore: PreferencesStore,
        private val authRepository: AuthRepository,
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsUiState())
        val state: StateFlow<SettingsUiState> = _state.asStateFlow()

        init {
            loadSettings()
        }

        private fun loadSettings() {
            viewModelScope.launch {
                val user = authRepository.getUserInfo()
                val themeStr = preferencesStore.getString("theme_mode", ThemeMode.DARK.name).first()
                val profile = preferencesStore.getString("color_profile", ColorProfiles.Default.name).first()
                val compactNavBar = preferencesStore.getBoolean("compact_nav_bar").first()
                val defaultPage = preferencesStore.getString("default_page", "dashboard").first()
                val threshold = preferencesStore.getUserInt("attendance_threshold", 75).first()
                val semesterEnd = preferencesStore.getUserString("semester_end_date", "").first()
                val combinedAttendance = preferencesStore.getUserBoolean("combined_attendance").first()
                val selectedYearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
                val selectedClassId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
                val semestersResult =
                    runCatching {
                        user?.let {
                            attendanceRepo.getSemesterOptions(it.admno, it.brId, false)
                        }.orEmpty()
                    }
                val semesters = semestersResult.getOrDefault(emptyList())
                val selectedSemester =
                    semesters.firstOrNull { it.yearId == selectedYearId && it.classId == selectedClassId }
                        ?: semesters.firstOrNull()

                _state.update {
                    it.copy(
                        userName = user?.name ?: "",
                        admno = user?.admno ?: "",
                        themeMode = ThemeMode.entries.find { m -> m.name == themeStr } ?: ThemeMode.DARK,
                        colorProfile = profile,
                        compactNavBar = compactNavBar,
                        defaultPage = defaultPage.takeIf { route -> route in SettingsUiState.validPages } ?: "dashboard",
                        threshold = threshold,
                        semesterEndDate = semesterEnd,
                        selectedSemester = selectedSemester,
                        semesterOptions = semesters,
                        semesterError = semestersResult.exceptionOrNull()?.message,
                        combinedAttendance = combinedAttendance,
                        deviceId = authRepository.getOrCreateDeviceId(),
                    )
                }
            }
        }

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch {
                preferencesStore.putString("theme_mode", mode.name)
                _state.update { it.copy(themeMode = mode) }
            }
        }

        fun setColorProfile(name: String) {
            viewModelScope.launch {
                preferencesStore.putString("color_profile", name)
                _state.update { it.copy(colorProfile = name) }
            }
        }

        fun setCompactNavBar(enabled: Boolean) {
            viewModelScope.launch {
                preferencesStore.putBoolean("compact_nav_bar", enabled)
                _state.update { it.copy(compactNavBar = enabled) }
            }
        }

        fun setDefaultPage(route: String) {
            if (route !in SettingsUiState.validPages) return
            viewModelScope.launch {
                preferencesStore.putString("default_page", route)
                _state.update { it.copy(defaultPage = route) }
            }
        }

        fun setThreshold(value: Int) {
            viewModelScope.launch {
                val clamped = value.coerceIn(50, 95)
                preferencesStore.putUserInt("attendance_threshold", clamped)
                _state.update { it.copy(threshold = clamped) }
            }
        }

        fun setSemesterEndDate(date: String) {
            viewModelScope.launch {
                preferencesStore.putUserString("semester_end_date", date)
                _state.update { it.copy(semesterEndDate = date) }
            }
        }

        fun setSelectedSemester(option: SemesterOption) {
            viewModelScope.launch {
                preferencesStore.putUserString(SELECTED_SEMESTER_YEAR_KEY, option.yearId)
                preferencesStore.putUserString(SELECTED_SEMESTER_CLASS_KEY, option.classId)
                _state.update { it.copy(selectedSemester = option) }
            }
        }

        fun setCombinedAttendance(enabled: Boolean) {
            viewModelScope.launch {
                preferencesStore.putUserBoolean("combined_attendance", enabled)
                _state.update { it.copy(combinedAttendance = enabled) }
            }
        }

        fun setDeviceId(id: String) {
            val trimmed = id.trim()
            if (trimmed.isBlank()) return
            authRepository.setDeviceId(trimmed)
            _state.update { it.copy(deviceId = trimmed) }
        }

        fun clearCache() {
            viewModelScope.launch {
                _state.update { it.copy(isClearing = true) }
                try {
                    attendanceRepo.clearCache()
                    timetableRepo.clearCache()
                    clearGeneratedFiles()
                } finally {
                    _state.update { it.copy(isClearing = false) }
                }
            }
        }

        fun logout(onLoggedOut: () -> Unit) {
            viewModelScope.launch {
                attendanceRepo.clearCache()
                timetableRepo.clearCache()
                clearGeneratedFiles()
                preferencesStore.clearUserScoped()
                authRepository.logout()
                onLoggedOut()
            }
        }

        private fun clearGeneratedFiles() {
            listOf("report_cards").forEach { child ->
                appContext.cacheDir.resolve(child).deleteRecursively()
            }
        }
    }
