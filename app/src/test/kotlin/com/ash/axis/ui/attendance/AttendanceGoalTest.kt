package com.ash.axis.ui.attendance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AttendanceGoalTest {
    @Test
    fun `drag position snaps to saved goal steps`() {
        assertEquals(50, goalAtPosition(0f, 200f))
        assertEquals(50, goalAtPosition(100f, 200f))
        assertEquals(75, goalAtPosition(150f, 200f))
        assertEquals(95, goalAtPosition(200f, 200f))
    }
}
