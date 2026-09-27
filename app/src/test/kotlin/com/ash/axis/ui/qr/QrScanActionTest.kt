package com.ash.axis.ui.qr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QrScanActionTest {
    @Test
    fun `recognition mode cannot request attendance submission`() {
        assertEquals(QrScanAction.RECOGNIZED_ONLY, qrScanAction(QrScanMode.RECOGNITION_ONLY, true, false))
        assertEquals(QrScanAction.SUBMIT, qrScanAction(QrScanMode.ATTENDANCE, true, false))
        assertEquals(QrScanAction.IGNORE, qrScanAction(QrScanMode.ATTENDANCE, false, false))
        assertEquals(QrScanAction.IGNORE, qrScanAction(QrScanMode.ATTENDANCE, true, true))
    }
}
