package com.ash.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ash.core.util.Result

@Composable
fun <T> LoadingStateContainer(
    result: Result<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val visualState =
        when (result) {
            is Result.Loading -> LoadingVisualState.LOADING
            is Result.Error -> LoadingVisualState.ERROR
            is Result.Success -> LoadingVisualState.SUCCESS
        }
    Crossfade(
        targetState = visualState,
        modifier = modifier,
        animationSpec = tween(300),
        label = "loading-state",
    ) { state ->
        when (state) {
            LoadingVisualState.LOADING -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AnimatedIconLoader()
                }
            }
            LoadingVisualState.ERROR -> {
                val error = result as? Result.Error
                ErrorContent(
                    message = error?.message ?: "Something went wrong",
                    onRetry = onRetry,
                )
            }
            LoadingVisualState.SUCCESS -> {
                val success = result as? Result.Success ?: return@Crossfade
                content(success.data)
            }
        }
    }
}

private enum class LoadingVisualState { LOADING, ERROR, SUCCESS }

@Composable
private fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            onRetry?.let { retry ->
                Button(onClick = retry) {
                    Text("Try again")
                }
            }
        }
    }
}
