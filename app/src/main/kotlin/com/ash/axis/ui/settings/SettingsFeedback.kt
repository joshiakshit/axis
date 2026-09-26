package com.ash.axis.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable

@Composable
internal fun BugsAndFixesSettings(context: Context) {
    SettingsCard {
        ActionRow("Bugs and fixes", "github.com/joshiakshit/axis") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/joshiakshit/axis")))
        }
    }
}
