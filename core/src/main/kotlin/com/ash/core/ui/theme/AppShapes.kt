package com.ash.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape

object AppShapes {
    val small = RoundedCornerShape(AppDimens.radiusSm)
    val medium = RoundedCornerShape(AppDimens.radiusMd)
    val large = RoundedCornerShape(AppDimens.radiusLg)
    val full = RoundedCornerShape(percent = 50)
}
