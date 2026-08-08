package com.ash.axis.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.session.UsageReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    ) : ViewModel() {
        private val _state = MutableStateFlow(QrScanUiState())
        val state: StateFlow<QrScanUiState> = _state.asStateFlow()

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
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
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
                        )
                    usageReporter.log(if (result.success == true) UsageReporter.QR_SCAN else UsageReporter.QR_FAIL)
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = result.message,
                            success = result.success,
                        )
                    }
                } catch (e: Exception) {
                    usageReporter.log(UsageReporter.QR_FAIL)
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = e.message ?: "QR attendance failed",
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
    }
