package com.ash.axis.ui.academics

import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.domain.model.SemesterOption
import com.ash.core.storage.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Suppress("TooGenericExceptionCaught")
internal fun selectedSemester(
    coordinator: AcademicDataCoordinator,
    attendance: AttendanceRepository,
    preferences: PreferencesStore,
): Flow<SemesterOption?> =
    coordinator.activeContext.flatMapLatest { context ->
        if (context == null) {
            flowOf(null)
        } else {
            combine(
                preferences.getUserString(SELECTED_SEMESTER_YEAR_KEY),
                preferences.getUserString(SELECTED_SEMESTER_CLASS_KEY),
            ) { year, classId -> year to classId }
                .distinctUntilChanged()
                .flatMapLatest { (year, classId) ->
                    flow {
                        if (year.isNotBlank() && classId.isNotBlank()) emit(SemesterOption(year, classId, ""))
                        val resolved =
                            try {
                                attendance.getPreferredSemester(context.admno, context.brId, year, classId)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                if (year.isBlank() || classId.isBlank()) throw error
                                null
                            }
                        if (resolved != null) emit(resolved)
                    }
                }
        }
    }
