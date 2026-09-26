package com.ash.axis.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ash.axis.R

@Suppress("TopLevelPropertyNaming")
private const val PHONE_MAX_LENGTH = 10

@Composable
internal fun PhoneStep(
    state: LoginUiState,
    viewModel: LoginViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_axis_logo),
            contentDescription = "Axis",
            modifier = Modifier.size(width = 80.dp, height = 60.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(36.dp))
        TextField(
            value = state.phone,
            onValueChange = { value -> viewModel.onPhoneChanged(value.filter { it in '0'..'9' }.take(PHONE_MAX_LENGTH)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Phone number") },
            prefix = { Text("+91 ") },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                ),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = viewModel::requestOtp,
            enabled = !state.isLoading && state.phone.length == PHONE_MAX_LENGTH,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Submit")
            }
        }
        state.error?.let { error ->
            Spacer(Modifier.height(20.dp))
            ErrorBanner(error)
        }
    }
}
