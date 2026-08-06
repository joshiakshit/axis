package com.ash.axis.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.ColorProfiles
import com.ash.core.ui.theme.LocalIsDarkTheme
import com.ash.core.ui.theme.ThemeMode
import com.ash.core.ui.theme.cardColor

@Composable
internal fun ThemeSelector(
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    SettingsCard {
        Text("Theme", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                val selected = currentMode == mode
                Surface(
                    modifier = Modifier.weight(1f),
                    selected = selected,
                    onClick = { onSelect(mode) },
                    shape = AppShapes.small,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            cardColor()
                        },
                    border =
                        BorderStroke(
                            1.dp,
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
                            },
                        ),
                ) {
                    Text(
                        mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(vertical = 9.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
internal fun ColorProfileSelector(
    currentProfile: String,
    onSelect: (String) -> Unit,
) {
    SettingsCard {
        Text("Color theme", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ColorProfiles.isDynamicSupported()) {
                val context = LocalContext.current
                val isDark = LocalIsDarkTheme.current
                val dynamicPrimary =
                    remember(context, isDark) {
                        if (isDark) {
                            dynamicDarkColorScheme(context).primary
                        } else {
                            dynamicLightColorScheme(context).primary
                        }
                    }
                ColorProfileChip(
                    name = ColorProfiles.DYNAMIC_NAME,
                    color = dynamicPrimary,
                    selected = currentProfile == ColorProfiles.DYNAMIC_NAME,
                    onClick = { onSelect(ColorProfiles.DYNAMIC_NAME) },
                )
            }
            ColorProfiles.all.forEach { profile ->
                ColorProfileChip(
                    name = profile.name,
                    color = profile.primary,
                    selected = currentProfile == profile.name,
                    onClick = { onSelect(profile.name) },
                )
            }
        }
    }
}

@Composable
private fun ColorProfileChip(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = AppShapes.full,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                cardColor()
            },
        border =
            BorderStroke(
                1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (color.luminance() > 0.5f) Color.Black else Color.White),
                    )
                }
            }
            Text(
                name.replaceFirstChar { it.uppercase() },
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
