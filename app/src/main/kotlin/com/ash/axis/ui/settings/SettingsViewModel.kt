package com.ash.axis.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.export.DataExporter
import com.ash.axis.data.export.ExportFile
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.session.UsageReporter
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
    val accentHex: String = "",
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferencesStore: PreferencesStore,
        private val authRepository: AuthRepository,
        private val academic: AcademicDataCoordinator,
        private val dataExporter: DataExporter,
        private val usageReporter: UsageReporter,
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
                val accentHex = preferencesStore.getString("accent_color", "").first()
                _state.update {
                    it.copy(
                        userName = user?.name ?: "",
                        admno = user?.admno ?: "",
                        themeMode = ThemeMode.entries.find { m -> m.name == themeStr } ?: ThemeMode.DARK,
                        accentHex = ColorProfiles.presetHex(accentHex),
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

        fun setAccent(hex: String) {
            val preset = ColorProfiles.presetHex(hex)
            viewModelScope.launch {
                preferencesStore.putString("accent_color", preset)
                _state.update { it.copy(accentHex = preset) }
            }
        }

        fun exportAttendance() = runExport { dataExporter.exportAttendancePng() }

        fun exportTimetable() = runExport { dataExporter.exportTimetablePng() }

        fun downloadAttendance() = runDownload { dataExporter.exportAttendancePng() }

        fun downloadTimetable() = runDownload { dataExporter.exportTimetablePng() }

        fun consumeExportMessage() {
            if (_state.value.exportMessage != null) _state.update { it.copy(exportMessage = null) }
        }

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
                    usageReporter.log(UsageReporter.EXPORT)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
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
                    share(withContext(Dispatchers.IO) { block() })
                    usageReporter.log(UsageReporter.EXPORT)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
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

        fun logout(onLoggedOut: () -> Unit) {
            viewModelScope.launch {
                academic.deactivate()
                academic.clearAcademicCache()
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
    }
