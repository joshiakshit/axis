package com.ash.axis.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AttendanceUseCaseTest {
    private val useCase = AttendanceUseCase()

    @Test
    fun `bunkBudget returns correct count when above threshold`() {
        assertEquals(20, useCase.bunkBudget(attended = 90, total = 100, threshold = 75))
    }

    @Test
    fun `bunkBudget returns zero when at threshold`() {
        assertEquals(0, useCase.bunkBudget(attended = 75, total = 100, threshold = 75))
    }

    @Test
    fun `bunkBudget returns zero when below threshold`() {
        assertEquals(0, useCase.bunkBudget(attended = 10, total = 20, threshold = 75))
    }

    @Test
    fun `bunkBudget handles zero total`() {
        assertEquals(0, useCase.bunkBudget(attended = 0, total = 0, threshold = 75))
    }

    @Test
    fun `mustAttend returns correct count when below threshold`() {
        assertEquals(8, useCase.mustAttend(attended = 13, total = 20, threshold = 75))
    }

    @Test
    fun `mustAttend returns zero when at or above threshold`() {
        assertEquals(0, useCase.mustAttend(attended = 80, total = 100, threshold = 75))
    }

    @Test
    fun `mustAttend handles zero total`() {
        assertEquals(0, useCase.mustAttend(attended = 0, total = 0, threshold = 75))
    }

    @Test
    fun `tone OK when above threshold`() {
        assertEquals(AttendanceTone.OK, useCase.tone(80.0, 75))
    }

    @Test
    fun `tone WARN when slightly below threshold`() {
        assertEquals(AttendanceTone.WARN, useCase.tone(70.0, 75))
    }

    @Test
    fun `tone BAD when well below threshold`() {
        assertEquals(AttendanceTone.BAD, useCase.tone(50.0, 75))
    }

    @Test
    fun `atRiskCount counts subjects below threshold`() {
        val subjects =
            listOf(
                SubjectAttendance("A", "Math", "LEC", 80, 100, 80.0),
                SubjectAttendance("B", "Phys", "LEC", 60, 100, 60.0),
                SubjectAttendance("C", "Chem", "LEC", 70, 100, 70.0),
            )
        assertEquals(2, useCase.atRiskCount(subjects, 75))
    }

    @Test
    fun `totalBunkable sums budget for subjects above threshold`() {
        val subjects =
            listOf(
                SubjectAttendance("A", "Math", "LEC", 90, 100, 90.0),
                SubjectAttendance("B", "Phys", "LEC", 60, 100, 60.0),
            )
        assertEquals(20, useCase.totalBunkable(subjects, 75))
    }
}
