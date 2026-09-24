package com.ash.axis.ui.dashboard

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardAvailabilityTest {
    @Test
    fun `attendance content remains visible when timetable fails`() {
        val state = DashboardUiState(hasAttendance = true, timetableError = "Unavailable")

        assertFalse(state.needsFullScreenLoading())
        assertFalse(state.needsFullScreenError())
    }

    @Test
    fun `timetable content remains visible when attendance fails`() {
        val state = DashboardUiState(hasTimetable = true, attendanceError = "Unavailable")

        assertFalse(state.needsFullScreenLoading())
        assertFalse(state.needsFullScreenError())
    }

    @Test
    fun `no data and a failed request shows an error`() {
        val state = DashboardUiState(attendanceError = "Unavailable")

        assertFalse(state.needsFullScreenLoading())
        assertTrue(state.needsFullScreenError())
    }
}
