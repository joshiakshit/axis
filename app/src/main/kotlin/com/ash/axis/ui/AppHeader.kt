package com.ash.axis.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.R

@Composable
internal fun AppHeader(
    onSettingsClick: () -> Unit,
    accountName: String = "",
    hasMultipleAccounts: Boolean = false,
    onAccountClick: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_axis_logo),
            contentDescription = "Axis",
            modifier = Modifier.size(width = 24.dp, height = 18.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        AccountAvatar(
            name = accountName,
            highlighted = hasMultipleAccounts,
            onClick = onAccountClick,
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onSettingsClick) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountAvatar(
    name: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Surface(
        modifier =
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        shape = CircleShape,
        color =
            if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initial,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color =
                    if (highlighted) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}
