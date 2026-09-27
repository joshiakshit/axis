package com.ash.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val DarkBg = Color(0xFF000000)
private val DarkOnBg = Color(0xFFF2F2F4)
private val DarkSurface = Color(0xFF0C0C0E)
private val DarkSurfaceVariant = Color(0xFF151517)
private val DarkOnSurfaceVariant = Color(0xFF8A8A90)
private val DarkOutline = Color(0xFF262628)

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
)

object ColorProfiles {
    val Slate =
        ColorProfile(
            name = "slate",
            primary = Color(0xFF6E90C0),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF16202C),
            onPrimaryContainer = Color(0xFFB4C6DA),
            secondary = Color(0xFF8FA9CC),
            onSecondary = Color.White,
            background = DarkBg,
            onBackground = DarkOnBg,
            surface = DarkSurface,
            onSurface = DarkOnBg,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline,
        )

    val Default = Slate

    val all = listOf(Slate)

    const val DYNAMIC_NAME = "dynamic"

    fun byName(name: String): ColorProfile = all.find { it.name == name } ?: Default

    // A blank hex keeps the default profile.
    val accentPresets =
        listOf(
            AccentPreset("Slate", ""),
            AccentPreset("Azure", "4E9BF5"),
            AccentPreset("Teal", "2CC2B0"),
            AccentPreset("Emerald", "46C079"),
            AccentPreset("Amber", "E6A93C"),
            AccentPreset("Coral", "F07B54"),
            AccentPreset("Rose", "F0699C"),
            AccentPreset("Violet", "9B87F5"),
        )

    fun presetHex(hex: String): String =
        accentPresets.firstOrNull { it.hex.equals(hex.trim().removePrefix("#"), ignoreCase = true) }?.hex.orEmpty()

    fun parseAccent(hex: String): Color? {
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6 || cleaned.any { it.digitToIntOrNull(16) == null }) return null
        val value = cleaned.toLongOrNull(16) ?: return null
        return Color(0xFF000000 or value)
    }

    fun accented(accent: Color): ColorProfile {
        val onAccent = if (accent.luminance() > CONTRAST_SPLIT) Color.Black else Color.White
        return Slate.copy(
            name = "accent",
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = DarkBg.mix(accent, 0.16f),
            onPrimaryContainer = accent.mix(Color.White, 0.35f),
            secondary = accent.mix(Color.White, 0.18f),
            onSecondary = onAccent,
        )
    }

    private const val CONTRAST_SPLIT = 0.5f
}

data class AccentPreset(
    val label: String,
    val hex: String,
)

private fun Color.mix(
    other: Color,
    ratio: Float,
): Color =
    Color(
        red = red * (1 - ratio) + other.red * ratio,
        green = green * (1 - ratio) + other.green * ratio,
        blue = blue * (1 - ratio) + other.blue * ratio,
    )
