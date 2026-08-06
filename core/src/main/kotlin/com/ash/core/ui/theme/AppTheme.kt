package com.ash.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalIsDarkTheme = staticCompositionLocalOf { true }

@Composable
fun cardColor(): Color =
    if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }

@Composable
fun highlightColor(alpha: Float = 0.15f): Color =
    if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.55f)
    }

private val AppFontFamily = FontFamily.SansSerif

private val AppTypography =
    Typography(
        displayLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp),
        displayMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp),
        displaySmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp),
        headlineLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
        headlineMedium =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.1.sp,
            ),
        titleSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTheme(
    themeState: ThemeState = ThemeState(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeState.mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

    val useDynamicColor = dynamicColor || themeState.profileName == ColorProfiles.DYNAMIC_NAME
    val colorScheme =
        when {
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> {
                val profile = ColorProfiles.byName(themeState.profileName)
                if (isDark) profile.toDarkScheme() else profile.toLightScheme()
            }
        }

    val rippleConfiguration =
        RippleConfiguration(
            color = colorScheme.primary,
            rippleAlpha =
                RippleAlpha(
                    pressedAlpha = 0.10f,
                    focusedAlpha = 0.10f,
                    draggedAlpha = 0.08f,
                    hoveredAlpha = 0.04f,
                ),
        )

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
                content()
            }
        }
    }
}

private fun ColorProfile.toDarkScheme() =
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outline,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceVariant,
        surfaceContainerHigh = surfaceVariant.blend(onSurface, 0.06f),
        surfaceContainerHighest = surfaceVariant.blend(onSurface, 0.12f),
    )

private fun ColorProfile.toLightScheme(): androidx.compose.material3.ColorScheme {
    val base = androidx.compose.ui.graphics.Color(0xFFFDFBFF)
    val tinted = base.blend(primary, 0.04f)
    val surfVar = base.blend(primary, 0.08f)
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = base.blend(primary, 0.12f),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = onSecondary,
        background = tinted,
        onBackground = androidx.compose.ui.graphics.Color(0xFF1A1C1E),
        surface = tinted,
        onSurface = androidx.compose.ui.graphics.Color(0xFF1A1C1E),
        surfaceVariant = surfVar,
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF44474F),
        outline = androidx.compose.ui.graphics.Color(0xFF74777F),
        outlineVariant = base.blend(primary, 0.12f),
        surfaceContainerLowest = base,
        surfaceContainerLow = base.blend(primary, 0.03f),
        surfaceContainer = base.blend(primary, 0.06f),
        surfaceContainerHigh = base.blend(primary, 0.09f),
        surfaceContainerHighest = base.blend(primary, 0.12f),
    )
}

private fun androidx.compose.ui.graphics.Color.blend(
    other: androidx.compose.ui.graphics.Color,
    ratio: Float,
): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(
        red = red * (1 - ratio) + other.red * ratio,
        green = green * (1 - ratio) + other.green * ratio,
        blue = blue * (1 - ratio) + other.blue * ratio,
    )
