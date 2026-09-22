package com.ash.axis.ui.qr

import com.ash.axis.data.repository.IcloudServerException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

class QrDiagnosticsTest {
    @Test
    fun `recognition mode cannot request attendance submission`() {
        assertEquals(QrScanAction.RECOGNIZED_ONLY, qrScanAction(QrScanMode.RECOGNITION_ONLY, true, false))
        assertEquals(QrScanAction.SUBMIT, qrScanAction(QrScanMode.ATTENDANCE, true, false))
        assertEquals(QrScanAction.IGNORE, qrScanAction(QrScanMode.ATTENDANCE, false, false))
        assertEquals(QrScanAction.IGNORE, qrScanAction(QrScanMode.ATTENDANCE, true, true))
    }

    @Test
    fun `diagnostic events omit supplied secrets and log one frame summary per second`() {
        var now = 0L
        val lines = mutableListOf<String>()
        val diagnostics = QrDiagnostics(enabled = true, clockMs = { now }, logger = lines::add)
        diagnostics.start(QrScanMode.RECOGNITION_ONLY)
        diagnostics.stage(QrStage.SELFIE)
        diagnostics.frame(1280, 720, 90, 1f, 1f)
        now = 1100
        diagnostics.decodeMiss("mlkit", 12)
        diagnostics.frame(1280, 720, 90, 1f, 1f)
        diagnostics.error(IllegalStateException("token=private-student-id"), 500, 88)
        diagnostics.error(IcloudServerException(403, "student=private-student-id"), 403, 90)

        assertEquals(2, diagnostics.state.value.frameCount)
        assertTrue(lines.any { it.contains("frames=2") })
        assertTrue(lines.any { it.contains("status=500") })
        assertTrue(lines.any { it.contains("status=403") && it.contains("kind=http") })
        assertFalse(lines.joinToString().contains("private-student-id"))
        assertFalse(lines.joinToString().contains("token="))
    }

    @Test
    fun `release diagnostics emit nothing`() {
        val lines = mutableListOf<String>()
        val diagnostics = QrDiagnostics(enabled = false, logger = lines::add)
        diagnostics.start(QrScanMode.ATTENDANCE)
        diagnostics.frame(1280, 720, 0, 1f, 1f)
        diagnostics.error(IllegalStateException("private"), null, 10)

        assertTrue(lines.isEmpty())
        assertEquals(0, diagnostics.state.value.frameCount)
    }

    @Test
    fun `refresh 401 is recorded as a session failure`() {
        val lines = mutableListOf<String>()
        val diagnostics = QrDiagnostics(enabled = true, logger = lines::add)
        val error = HttpException(Response.error<Unit>(401, "Session not active".toResponseBody()))

        diagnostics.start(QrScanMode.ATTENDANCE)
        diagnostics.error(error, 401, 150)

        assertTrue(lines.any { it.contains("status=401") && it.contains("kind=session") })
    }
}
