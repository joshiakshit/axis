package com.ash.axis.data.academic

import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.domain.model.AttendanceResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

internal val AcademicSemesterSelection.attendanceKey: AttendanceKey?
    get() = context?.let { owner -> option?.let { AttendanceKey(owner, it.classId, it.yearId) } }

internal fun AcademicDataCoordinator.attendanceSelection(): Flow<AcademicSemesterSelection> =
    combine(activeContext, selectedSemester) { context, selection ->
        if (selection.context == context) selection else AcademicSemesterSelection(context)
    }

@OptIn(ExperimentalCoroutinesApi::class)
internal fun AcademicDataCoordinator.observeAttendance(
    repository: AttendanceRepository,
    onKeyChanged: () -> Unit,
): Flow<Pair<AcademicSemesterSelection, AcademicSnapshot<AttendanceResponse>>> =
    flow {
        var previousKey: AttendanceKey? = null
        emitAll(
            attendanceSelection().flatMapLatest { selection ->
                flow {
                    val key = selection.attendanceKey
                    if (key != previousKey) {
                        previousKey = key
                        onKeyChanged()
                        emit(selection to AcademicSnapshot<AttendanceResponse>())
                    }
                    if (key == null) {
                        emit(selection to AcademicSnapshot<AttendanceResponse>())
                    } else {
                        repository.observeSummary(key).collect { emit(selection to it) }
                    }
                }
            },
        )
    }
