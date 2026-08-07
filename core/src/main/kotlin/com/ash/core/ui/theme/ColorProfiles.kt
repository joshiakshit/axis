package com.ash.core.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color

// Shared pitch-black dark-mode neutrals. The single accent profile sits on this true-black, de-blued canvas.
private val DarkBg = Color(0xFF000000) // true black canvas (OLED)
private val DarkOnBg = Color(0xFFF2F2F4) // near-white text (softened)
private val DarkSurface = Color(0xFF0C0C0E) // cards: barely-raised near-black
private val DarkSurfaceVariant = Color(0xFF151517)
private val DarkOnSurfaceVariant = Color(0xFF8A8A90) // neutral gray (de-blued)
private val DarkOutline = Color(0xFF262628) // hairline neutral border

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
    // Single, toned-down accent. Muted steel-blue instead of the old vivid blue — used sparingly on black.
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

    fun isDynamicSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun byName(name: String): ColorProfile = all.find { it.name == name } ?: Default
}
