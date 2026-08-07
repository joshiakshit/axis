package com.ash.axis.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.export.DataExporter
import com.ash.axis.data.export.ExportFile
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.ui.ErrorText
import com.ash.core.storage.PreferencesStore
import com.ash.core.ui.theme.ColorProfiles
import com.ash.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "",
    val admno: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val colorProfile: String = ColorProfiles.Default.name,
    val accentHex: String = "",
    val customAccents: List<String> = emptyList(),
    val threshold: Int = 75,
    val semesterEndDate: String = "",
    val selectedSemester: SemesterOption? = null,
    val semesterOptions: List<SemesterOption> = emptyList(),
    val semesterError: String? = null,
    val combinedAttendance: Boolean = false,
    val isClearing: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val deviceId: String = "",
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferencesStore: PreferencesStore,
        private val authRepository: AuthRepository,
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val dataExporter: DataExporter,
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
                val accentHex = preferencesStore.getString("accent_color", "").first()
                val customAccents = parseCustomAccents(preferencesStore.getString("accent_customs", "").first())
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
                        accentHex = accentHex,
                        customAccents = customAccents,
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

        // Apply an accent. Blank clears the override (back to the hand-tuned default). A valid custom hex
        // (not one of the built-in presets) is remembered for quick re-picking.
        fun setAccent(hex: String) {
            val normalized = hex.trim().removePrefix("#").uppercase()
            if (normalized.isNotEmpty() && ColorProfiles.parseAccent(normalized) == null) return
            viewModelScope.launch {
                preferencesStore.putString("accent_color", normalized)
                val isPreset = ColorProfiles.accentPresets.any { it.hex.equals(normalized, ignoreCase = true) }
                val customs =
                    if (normalized.isBlank() || isPreset) {
                        _state.value.customAccents
                    } else {
                        (listOf(normalized) + _state.value.customAccents.filterNot { it.equals(normalized, true) })
                            .take(MAX_CUSTOM_ACCENTS)
                    }
                if (customs != _state.value.customAccents) {
                    preferencesStore.putString("accent_customs", customs.joinToString(","))
                }
                _state.update { it.copy(accentHex = normalized, customAccents = customs) }
            }
        }

        fun exportAttendance() = runExport { dataExporter.exportAttendanceCsv() }

        fun exportTimetable() = runExport { dataExporter.exportTimetableIcs() }

        fun downloadAttendance() = runDownload { dataExporter.exportAttendancePdf() }

        fun downloadTimetable() = runDownload { dataExporter.exportTimetablePdf() }

        fun consumeExportMessage() {
            if (_state.value.exportMessage != null) _state.update { it.copy(exportMessage = null) }
        }

        // Generate a PDF and drop it straight into the phone's Downloads (falling back to sharing on very
        // old Android versions that can't write there without a permission prompt).
        @Suppress("TooGenericExceptionCaught")
        private fun runDownload(block: suspend () -> ExportFile) {
            if (_state.value.isExporting) return
            viewModelScope.launch {
                _state.update { it.copy(isExporting = true) }
                try {
                    val export = withContext(Dispatchers.IO) { block() }
                    val saved = withContext(Dispatchers.IO) { dataExporter.saveToDownloads(export) }
                    if (saved) {
                        _state.update { it.copy(exportMessage = "Saved to Downloads: ${export.file.name}") }
                    } else {
                        share(export)
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(exportMessage = ErrorText.forData(e)) }
                } finally {
                    _state.update { it.copy(isExporting = false) }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun runExport(block: suspend () -> ExportFile) {
            if (_state.value.isExporting) return
            viewModelScope.launch {
                _state.update { it.copy(isExporting = true) }
                try {
                    share(block())
                } catch (e: Exception) {
                    _state.update { it.copy(exportMessage = ErrorText.forData(e)) }
                } finally {
                    _state.update { it.copy(isExporting = false) }
                }
            }
        }

        private fun share(export: ExportFile) {
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", export.file)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = export.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, export.subject)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            appContext.startActivity(
                Intent.createChooser(intent, export.subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        private fun parseCustomAccents(raw: String): List<String> =
            raw.split(",")
                .map { it.trim().uppercase() }
                .filter { ColorProfiles.parseAccent(it) != null }
                .distinct()
                .take(MAX_CUSTOM_ACCENTS)

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
            listOf("report_cards", "exports").forEach { child ->
                appContext.cacheDir.resolve(child).deleteRecursively()
            }
        }

        private companion object {
            const val MAX_CUSTOM_ACCENTS = 6
        }
    }
