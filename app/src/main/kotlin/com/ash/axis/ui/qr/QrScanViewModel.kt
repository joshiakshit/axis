package com.ash.axis.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.BuildConfig
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.IcloudServerException
import com.ash.axis.data.session.UsageReporter
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.ui.ErrorText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class QrScanUiState(
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val success: Boolean? = null,
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class QrScanViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val authRepository: AuthRepository,
        private val usageReporter: UsageReporter,
        private val academic: AcademicDataCoordinator,
    ) : ViewModel() {
        private val _state = MutableStateFlow(QrScanUiState())
        val state: StateFlow<QrScanUiState> = _state.asStateFlow()
        internal val diagnostics = QrDiagnostics(BuildConfig.DEBUG)

        fun clearMessage() {
            _state.update { it.copy(message = null, success = null) }
        }

        fun showMessage(message: String) {
            _state.update { it.copy(message = message, success = false) }
        }

        fun submitQrScan(
            rawQr: String,
            userSelfie: String,
        ) {
            if (rawQr.isBlank()) {
                _state.update { it.copy(message = "No QR data scanned", success = false) }
                return
            }
            if (userSelfie.isBlank()) {
                _state.update { it.copy(message = "Selfie image is required for QR attendance", success = false) }
                return
            }

            viewModelScope.launch {
                _state.update { it.copy(isSubmitting = true, message = null, success = null) }
                val startedAt = System.nanoTime()
                try {
                    diagnostics.stage(QrStage.AUTHENTICATION)
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val origin = StudentRequestContext(user.admno, user.brId, user.clientId, user.academicYear)
                    val result =
                        attendanceRepo.sendScanQR(
                            rawQr = rawQr,
                            admno = user.admno,
                            email = user.email,
                            brId = user.brId,
                            latitude = FIXED_LATITUDE,
                            longitude = FIXED_LONGITUDE,
                            userSelfie = userSelfie,
                            clientId = user.clientId,
                            onSubmissionStart = { diagnostics.stage(QrStage.SUBMISSION) },
                        )
                    diagnostics.response(result.httpStatus, result.httpDurationMs ?: 0, result.success)
                    usageReporter.log(if (result.success == true) UsageReporter.QR_SCAN else UsageReporter.QR_FAIL)
                    val message = if (result.success == true) refreshMessage(origin, result.message) else result.message
                    if (!isCurrentOrigin(origin)) return@launch
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = message,
                            success = result.success,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val status = (e as? IcloudServerException)?.statusCode ?: (e as? HttpException)?.code()
                    diagnostics.error(e, status, (System.nanoTime() - startedAt) / 1_000_000)
                    usageReporter.log(UsageReporter.QR_FAIL)
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = ErrorText.forData(e),
                            success = false,
                        )
                    }
                }
            }
        }

        private companion object {
            const val FIXED_LATITUDE = 28.365857
            const val FIXED_LONGITUDE = 77.5404963
        }

        private fun sameAccount(
            left: StudentRequestContext,
            right: StudentRequestContext,
        ) = left.admno == right.admno && left.brId == right.brId && left.clientId == right.clientId

        private suspend fun refreshMessage(
            origin: StudentRequestContext,
            message: String,
        ): String =
            try {
                academic.qrSucceeded(origin)
                message
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                "$message Attendance refresh failed: ${ErrorText.forData(error)}"
            }

        private fun isCurrentOrigin(origin: StudentRequestContext): Boolean {
            val active = academic.activeContext.value
            val currentUser = authRepository.getUserInfo() ?: return false
            return (active == null || sameAccount(active, origin)) &&
                currentUser.admno == origin.admno && currentUser.brId == origin.brId && currentUser.clientId == origin.clientId
        }
    }
