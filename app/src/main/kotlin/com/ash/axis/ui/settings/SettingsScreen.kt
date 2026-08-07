package com.ash.axis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.core.ui.theme.AppDimens

@Suppress("LongMethod")
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(10.dp)) }
        item { SettingsHeader() }

        if (state.userName.isNotBlank()) {
            item { ProfileHeader(state.userName, state.admno) }
        }

        item { SectionLabel("APPEARANCE") }
        item { ThemeSelector(state.themeMode, viewModel::setThemeMode) }

        item { SectionLabel("ATTENDANCE") }
        item {
            AttendanceSettings(
                threshold = state.threshold,
                onThresholdChange = viewModel::setThreshold,
                selectedSemester = state.selectedSemester,
                semesterOptions = state.semesterOptions,
                semesterError = state.semesterError,
                onSemesterChange = viewModel::setSelectedSemester,
                semesterEndDate = state.semesterEndDate,
                onSemesterEndDateChange = viewModel::setSemesterEndDate,
                combinedAttendance = state.combinedAttendance,
                onCombinedAttendanceChange = viewModel::setCombinedAttendance,
            )
        }

        item { SectionLabel("SECURITY & DATA") }
        item { SecuritySettings(state, viewModel) }

        item { SectionLabel("SUPPORT & ABOUT") }
        item { SupportAboutSettings(context = context) }

        item {
            TextButton(
                onClick = { viewModel.logout(onLogout) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            }
        }

        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}
