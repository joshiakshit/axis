package com.ash.axis.ui.auth

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.data.repository.LoginMethod

@Suppress("TopLevelPropertyNaming")
private const val PHONE_MAX_LENGTH = 10

@Suppress("TopLevelPropertyNaming")
private const val PHONE_GROUP_SIZE = 5

@Suppress("LongMethod")
@Composable
internal fun PhoneStep(
    state: LoginUiState,
    viewModel: LoginViewModel,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PhoneStepHeader(method = state.method)
                LoginMethodToggle(
                    selected = state.method,
                    onSelected = viewModel::onMethodChanged,
                )
                Spacer(Modifier.height(28.dp))

                if (state.method == LoginMethod.PHONE) {
                    PhoneDisplay(phone = state.phone)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.7f),
                        thickness = 1.5.dp,
                        color =
                            if (state.phone.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                    )
                } else {
                    EmailInput(email = state.email, onEmailChanged = viewModel::onEmailChanged)
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(20.dp))
                    ErrorBanner(error)
                }
            }

            LoginFab(
                isLoading = state.isLoading,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Continue",
                onClick = viewModel::requestOtp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 20.dp),
            )
        }

        if (state.method == LoginMethod.PHONE) {
            NumPad(
                onDigit = { d ->
                    if (state.phone.length < PHONE_MAX_LENGTH) {
                        viewModel.onPhoneChanged(state.phone + d)
                    }
                },
                onDelete = {
                    if (state.phone.isNotEmpty()) {
                        viewModel.onPhoneChanged(state.phone.dropLast(1))
                    }
                },
            )
        } else {
            Spacer(Modifier.height(116.dp))
        }
    }
}

@Composable
private fun LoginMethodToggle(
    selected: LoginMethod,
    onSelected: (LoginMethod) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoginMethodTab(
            label = "Phone",
            selected = selected == LoginMethod.PHONE,
            onClick = { onSelected(LoginMethod.PHONE) },
        )
        Spacer(Modifier.width(24.dp))
        LoginMethodTab(
            label = "Email",
            selected = selected == LoginMethod.EMAIL,
            onClick = { onSelected(LoginMethod.EMAIL) },
        )
    }
}

@Composable
private fun LoginMethodTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(
                modifier = Modifier.width(32.dp),
                thickness = 2.dp,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
            )
        }
    }
}

@Composable
private fun PhoneStepHeader(method: LoginMethod) {
    Spacer(Modifier.height(72.dp))

    Text(
        if (method == LoginMethod.EMAIL) "Your Email" else "Your Phone",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = 0.sp,
    )

    Spacer(Modifier.height(10.dp))

    Text(
        if (method == LoginMethod.EMAIL) {
            "Enter your registered email ID.\nWe'll send you an OTP to verify."
        } else {
            "Enter your registered mobile number.\nWe'll send you an OTP to verify."
        },
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
    )

    Spacer(Modifier.height(28.dp))
}

@Composable
private fun EmailInput(
    email: String,
    onEmailChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChanged,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(
                Icons.Default.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        placeholder = { Text("registered@email.com") },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
        shape = MaterialTheme.shapes.large,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun PhoneDisplay(phone: String) {
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val activeColor = MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "+91",
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        repeat(PHONE_MAX_LENGTH) { i ->
            if (i == PHONE_GROUP_SIZE) {
                Spacer(Modifier.width(12.dp))
            }
            PhoneDigitSlot(
                char = phone.getOrNull(i),
                placeholderColor = placeholderColor,
                activeColor = activeColor,
            )
        }
    }
}

@Composable
private fun PhoneDigitSlot(
    char: Char?,
    placeholderColor: Color,
    activeColor: Color,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.width(20.dp),
    ) {
        AnimatedContent(
            targetState = char,
            transitionSpec = {
                if (targetState != null) {
                    slideInVertically(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    ) { it } + fadeIn(tween(100)) togetherWith
                        slideOutVertically { -it / 2 } + fadeOut(tween(80))
                } else {
                    slideInVertically { -it / 2 } + fadeIn(tween(80)) togetherWith
                        slideOutVertically(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessHigh,
                                ),
                        ) { it } + fadeOut(tween(100))
                }
            },
            label = "phone-digit",
        ) { digit ->
            Text(
                text = digit?.toString() ?: "-",
                fontSize = 26.sp,
                fontWeight = if (digit != null) FontWeight.Medium else FontWeight.Light,
                letterSpacing = 1.sp,
                color = if (digit != null) activeColor else placeholderColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
