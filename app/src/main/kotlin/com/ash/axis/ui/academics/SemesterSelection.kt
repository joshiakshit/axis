package com.ash.axis.ui.academics

import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.core.storage.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal data class SemesterSelection(
    val context: StudentRequestContext? = null,
    val option: SemesterOption? = null,
    val error: Throwable? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Suppress("TooGenericExceptionCaught")
internal fun selectedSemester(
    coordinator: AcademicDataCoordinator,
    attendance: AttendanceRepository,
    preferences: PreferencesStore,
    resolveLabel: Boolean = false,
    retry: Flow<Long> = flowOf(0L),
): Flow<SemesterSelection> =
    coordinator.activeContext.flatMapLatest { context ->
        if (context == null) {
            flowOf(SemesterSelection())
        } else {
            combine(
                preferences.getUserString(SELECTED_SEMESTER_YEAR_KEY),
                preferences.getUserString(SELECTED_SEMESTER_CLASS_KEY),
                retry,
            ) { year, classId, attempt -> Triple(year, classId, attempt) }
                .distinctUntilChanged()
                .flatMapLatest { (year, classId) ->
                    flow {
                        val saved = if (year.isNotBlank() && classId.isNotBlank()) SemesterOption(year, classId, "") else null
                        emit(SemesterSelection(context, saved))
                        if (!resolveLabel && year.isNotBlank() && classId.isNotBlank()) return@flow
                        val resolved =
                            try {
                                val resolved = attendance.getPreferredSemester(context.admno, context.brId, year, classId)
                                SemesterSelection(context, resolved)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                SemesterSelection(context, saved, error)
                            }
                        emit(resolved)
                    }
                }
        }
    }
