package com.ash.axis.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.LoginMethod
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.ui.ErrorText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val email: String = "",
    val method: LoginMethod = LoginMethod.PHONE,
    val otp: String = "",
    val otpUsername: String = "",
    val step: LoginStep = LoginStep.PHONE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOtpVerified: Boolean = false,
) {
    val contact: String
        get() = if (method == LoginMethod.EMAIL) email.trim() else phone.trim()
}

enum class LoginStep { PHONE, OTP }

sealed interface LoginEvent {
    data class LoginSuccess(val user: UserInfo) : LoginEvent
}

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(LoginUiState())
        val state: StateFlow<LoginUiState> = _state.asStateFlow()

        private val _events = MutableStateFlow<LoginEvent?>(null)
        val events: StateFlow<LoginEvent?> = _events.asStateFlow()

        fun onPhoneChanged(phone: String) {
            _state.update { it.copy(phone = phone, error = null) }
        }

        fun onEmailChanged(email: String) {
            _state.update { it.copy(email = email, error = null) }
        }

        fun onMethodChanged(method: LoginMethod) {
            _state.update { it.copy(method = method, otp = "", otpUsername = "", error = null) }
        }

        fun onOtpChanged(otp: String) {
            _state.update { it.copy(otp = otp, error = null) }
        }

        fun requestOtp() {
            val current = _state.value
            val contact = current.contact
            if (current.method == LoginMethod.PHONE && contact.length < 10) {
                _state.update { it.copy(error = "Enter a valid phone number") }
                return
            }
            if (current.method == LoginMethod.EMAIL && !isValidEmail(contact)) {
                _state.update { it.copy(error = "Enter a valid registered email ID") }
                return
            }

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                try {
                    val username = authRepository.requestOtp(contact = contact, method = current.method)
                    _state.update { it.copy(isLoading = false, step = LoginStep.OTP, otpUsername = username) }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, error = ErrorText.forLogin(e, otpStep = false)) }
                }
            }
        }

        fun validateOtp() {
            val current = _state.value
            val contact = current.contact
            val otp = current.otp.trim()
            if (otp.length < 4) {
                _state.update { it.copy(error = "Enter the OTP") }
                return
            }

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                try {
                    val user =
                        authRepository.validateOtp(
                            contact = contact,
                            otp = otp,
                            username = current.otpUsername.ifBlank { contact },
                        )
                    _state.update { it.copy(isLoading = false, isOtpVerified = true) }
                    kotlinx.coroutines.delay(OTP_VERIFIED_DELAY_MS)
                    _events.value = LoginEvent.LoginSuccess(user)
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, error = ErrorText.forLogin(e, otpStep = true)) }
                }
            }
        }

        fun goBackToPhone() {
            _state.update { it.copy(step = LoginStep.PHONE, otp = "", isOtpVerified = false, error = null) }
        }

        fun consumeEvent() {
            _events.value = null
        }

        fun resetState() {
            _state.value = LoginUiState()
            _events.value = null
        }

        companion object {
            private const val OTP_VERIFIED_DELAY_MS = 800L

            private fun isValidEmail(email: String): Boolean = email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"))
        }
    }
