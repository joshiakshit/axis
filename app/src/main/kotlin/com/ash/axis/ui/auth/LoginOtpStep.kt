package com.ash.axis.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Suppress("TopLevelPropertyNaming")
private const val OTP_LENGTH = 6

@Suppress("TopLevelPropertyNaming")
private const val RESEND_COOLDOWN_SECONDS = 30

@Composable
internal fun OtpStep(
    state: LoginUiState,
    viewModel: LoginViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OtpStepHeader(contact = state.phone)
        OutlinedTextField(
            value = state.otp,
            onValueChange = { value ->
                val otp = value.filter { it in '0'..'9' }.take(OTP_LENGTH)
                if (otp != state.otp) {
                    viewModel.onOtpChanged(otp)
                    if (otp.length == OTP_LENGTH) viewModel.validateOtp()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Verification code") },
            singleLine = true,
            enabled = !state.isLoading && !state.isOtpVerified,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.large,
        )
        if (state.isLoading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
        OtpResendRow(isLoading = state.isLoading, onResend = viewModel::requestOtp)

        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            ErrorBanner(error)
        }
    }
}

@Composable
private fun OtpStepHeader(contact: String) {
    Spacer(Modifier.height(60.dp))

    Surface(
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    Text(
        "Enter Code",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = (-0.5).sp,
    )

    Spacer(Modifier.height(10.dp))

    val maskedContact =
        if (contact.length >= 6) "+91 " + contact.take(2) + "****" + contact.takeLast(4) else contact

    Text(
        "We sent an SMS to $maskedContact",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
    )

    Spacer(Modifier.height(36.dp))
}

@Composable
private fun OtpResendRow(
    isLoading: Boolean,
    onResend: () -> Unit,
) {
    var secondsLeft by remember { mutableIntStateOf(RESEND_COOLDOWN_SECONDS) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    if (secondsLeft > 0) {
        Text(
            "Resend code in ${secondsLeft}s",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    } else {
        TextButton(
            onClick = {
                if (!isLoading) {
                    secondsLeft = RESEND_COOLDOWN_SECONDS
                    onResend()
                }
            },
        ) {
            Text(
                "Resend Code",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
