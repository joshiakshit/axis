package com.ash.axis.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.data.repository.LoginMethod
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
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OtpStepHeader(method = state.method, contact = state.contact)
                OtpDotsRow(otp = state.otp, isVerified = state.isOtpVerified)

                Spacer(Modifier.height(20.dp))

                OtpResendRow(
                    isLoading = state.isLoading,
                    onResend = viewModel::requestOtp,
                )

                state.error?.let { error ->
                    Spacer(Modifier.height(16.dp))
                    ErrorBanner(error)
                }
            }

            LoginFab(
                isLoading = state.isLoading,
                icon = Icons.Default.Check,
                contentDescription = "Verify",
                onClick = viewModel::validateOtp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 20.dp),
            )
        }

        NumPad(
            onDigit = { d ->
                if (state.otp.length < OTP_LENGTH) {
                    val newOtp = state.otp + d
                    viewModel.onOtpChanged(newOtp)
                    if (newOtp.length == OTP_LENGTH) viewModel.validateOtp()
                }
            },
            onDelete = {
                if (state.otp.isNotEmpty()) {
                    viewModel.onOtpChanged(state.otp.dropLast(1))
                }
            },
        )
    }
}

@Composable
private fun OtpStepHeader(
    method: LoginMethod,
    contact: String,
) {
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
        if (method == LoginMethod.EMAIL) {
            maskEmail(contact)
        } else if (contact.length >= 6) {
            "+91 " + contact.take(2) + "****" + contact.takeLast(4)
        } else {
            contact
        }

    Text(
        if (method == LoginMethod.EMAIL) {
            "We sent an email to $maskedContact"
        } else {
            "We sent an SMS to $maskedContact"
        },
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
    )

    Spacer(Modifier.height(36.dp))
}

private fun maskEmail(email: String): String {
    val parts = email.split("@", limit = 2)
    if (parts.size != 2) return email
    val name = parts[0]
    val maskedName =
        when {
            name.length <= 2 -> name
            else -> name.take(2) + "***"
        }
    return "$maskedName@${parts[1]}"
}

@Composable
private fun OtpDotsRow(
    otp: String,
    isVerified: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(OTP_LENGTH) { index ->
            val char = otp.getOrNull(index)
            val isFocused = index == otp.length && !isVerified
            OtpDot(char = char, isFocused = isFocused, isVerified = isVerified)
            if (index == 2) {
                Spacer(Modifier.width(18.dp))
            } else if (index < OTP_LENGTH - 1) {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun OtpDot(
    char: Char?,
    isFocused: Boolean,
    isVerified: Boolean,
) {
    val verifiedColor = Color(0xFF4CAF50)
    val targetColor =
        when {
            isVerified -> verifiedColor
            isFocused -> MaterialTheme.colorScheme.primary
            char != null -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        }
    val dotColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 250),
        label = "otp-dot-color",
    )

    Surface(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (char == null && !isFocused) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = dotColor,
                ) {}
            } else if (char == null && isFocused) {
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = dotColor,
                ) {}
            } else {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (targetState != null) {
                            slideInVertically(
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                            ) { it } + fadeIn(tween(150)) togetherWith
                                slideOutVertically { -it / 2 } + fadeOut(tween(100))
                        } else {
                            fadeIn(tween(100)) togetherWith fadeOut(tween(150))
                        }
                    },
                    label = "otp-digit",
                ) { digit ->
                    Text(
                        text = digit?.toString() ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = dotColor,
                    )
                }
            }
        }
    }
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
