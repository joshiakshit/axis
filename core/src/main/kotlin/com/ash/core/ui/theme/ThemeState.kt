package com.ash.core.ui.theme

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class ThemeState(
    val mode: ThemeMode = ThemeMode.DARK,
    val profileName: String = ColorProfiles.Default.name,
    // RRGGBB; blank or invalid values use the profile default.
    val accentHex: String = "",
)
