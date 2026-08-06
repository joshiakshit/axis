package com.ash.core.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color

data class ColorProfile(
    val name: String,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val ok: Color = Color(0xFF34C759),
    val warn: Color = Color(0xFFF59E0B),
    val bad: Color = Color(0xFFEF4444),
)

object ColorProfiles {
    val Iris =
        ColorProfile(
            name = "iris",
            primary = Color(0xFF7C5CFC),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF2A1F5E),
            onPrimaryContainer = Color(0xFFE0D6FF),
            secondary = Color(0xFF9B8AFF),
            onSecondary = Color.White,
            background = Color(0xFF0B0F17),
            onBackground = Color(0xFFE6EDF6),
            surface = Color(0xFF141A24),
            onSurface = Color(0xFFE6EDF6),
            surfaceVariant = Color(0xFF1A2230),
            onSurfaceVariant = Color(0xFF9AA6B8),
            outline = Color(0xFF222B3A),
        )

    val Forest =
        ColorProfile(
            name = "forest",
            primary = Color(0xFF34C759),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF0D3B1A),
            onPrimaryContainer = Color(0xFFB8F5C8),
            secondary = Color(0xFF66D98A),
            onSecondary = Color.White,
            background = Color(0xFF0B0F17),
            onBackground = Color(0xFFE6EDF6),
            surface = Color(0xFF141A24),
            onSurface = Color(0xFFE6EDF6),
            surfaceVariant = Color(0xFF1A2230),
            onSurfaceVariant = Color(0xFF9AA6B8),
            outline = Color(0xFF222B3A),
        )

    val Slate =
        ColorProfile(
            name = "slate",
            primary = Color(0xFF4B8EF5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF0D2B5E),
            onPrimaryContainer = Color(0xFFBBD5FF),
            secondary = Color(0xFF77AAFF),
            onSecondary = Color.White,
            background = Color(0xFF0B0F17),
            onBackground = Color(0xFFE6EDF6),
            surface = Color(0xFF141A24),
            onSurface = Color(0xFFE6EDF6),
            surfaceVariant = Color(0xFF1A2230),
            onSurfaceVariant = Color(0xFF9AA6B8),
            outline = Color(0xFF222B3A),
        )

    val Amber =
        ColorProfile(
            name = "amber",
            primary = Color(0xFFF59E0B),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF3D2700),
            onPrimaryContainer = Color(0xFFFFDEA1),
            secondary = Color(0xFFFFBB33),
            onSecondary = Color.Black,
            background = Color(0xFF0B0F17),
            onBackground = Color(0xFFE6EDF6),
            surface = Color(0xFF141A24),
            onSurface = Color(0xFFE6EDF6),
            surfaceVariant = Color(0xFF1A2230),
            onSurfaceVariant = Color(0xFF9AA6B8),
            outline = Color(0xFF222B3A),
        )

    val Crimson =
        ColorProfile(
            name = "crimson",
            primary = Color(0xFFEF4444),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF3B0D0D),
            onPrimaryContainer = Color(0xFFFFBBBB),
            secondary = Color(0xFFFF6B6B),
            onSecondary = Color.White,
            background = Color(0xFF0B0F17),
            onBackground = Color(0xFFE6EDF6),
            surface = Color(0xFF141A24),
            onSurface = Color(0xFFE6EDF6),
            surfaceVariant = Color(0xFF1A2230),
            onSurfaceVariant = Color(0xFF9AA6B8),
            outline = Color(0xFF222B3A),
        )

    val Default = Slate

    val all = listOf(Iris, Slate, Crimson)

    const val DYNAMIC_NAME = "dynamic"

    fun isDynamicSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun byName(name: String): ColorProfile = all.find { it.name == name } ?: Default
}
