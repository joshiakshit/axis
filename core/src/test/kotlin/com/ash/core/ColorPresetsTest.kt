package com.ash.core

import com.ash.core.ui.theme.ColorProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColorPresetsTest {
    @Test
    fun `saved custom colours fall back to slate while presets survive`() {
        assertEquals("", ColorProfiles.presetHex("123456"))
        assertEquals("", ColorProfiles.presetHex("invalid"))
        assertEquals("4E9BF5", ColorProfiles.presetHex("#4e9bf5"))
        assertEquals(8, ColorProfiles.accentPresets.size)
    }
}
