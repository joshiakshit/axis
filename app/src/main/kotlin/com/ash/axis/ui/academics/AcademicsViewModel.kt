package com.ash.axis.ui.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.domain.usecase.TimetableUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AcademicsViewModel
    @Inject
    constructor(
        private val coordinator: AcademicDataCoordinator,
        private val timetableUseCase: TimetableUseCase,
    ) : ViewModel() {
        fun onSettledPage(page: Int) {
            viewModelScope.launch {
                when (page) {
                    0 -> coordinator.attendanceVisible()
                    2 -> coordinator.plannerVisible(timetableUseCase.getCurrentWeekRange().first)
                }
            }
        }
    }
