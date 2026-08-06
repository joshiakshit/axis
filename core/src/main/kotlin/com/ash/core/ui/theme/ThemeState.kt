package com.ash.core.ui.theme

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    fun next(): ThemeMode =
        when (this) {
            LIGHT -> DARK
            DARK -> SYSTEM
            SYSTEM -> LIGHT
        }
}

data class ThemeState(
    val mode: ThemeMode = ThemeMode.DARK,
    val profileName: String = ColorProfiles.Default.name,
)
