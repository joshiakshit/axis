package com.ash.axis.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ash.axis.BuildConfig
import com.ash.core.ui.components.SubtleDivider

@Composable
internal fun SupportAboutSettings(context: Context) {
    SettingsCard {
        ActionRow(
            "Write a review to Ashborne",
            "Send feedback, ideas, or issues to Akshit",
        ) {
            openReviewEmail(context)
        }
        SubtleDivider(modifier = Modifier.padding(vertical = 6.dp))
        ActionRow("GitHub", "github.com/joshiakshit") {
            openGithubProfile(context)
        }
        SubtleDivider(modifier = Modifier.padding(vertical = 6.dp))
        ActionRow(
            "About Axis",
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        ) {}
    }
}

private fun openReviewEmail(context: Context) {
    val uri =
        Uri.parse(
            "mailto:akshit.24scse1011061@galgotiasuiversity.ac.in" +
                "?subject=${Uri.encode("Axis review")}" +
                "&body=${Uri.encode("Hi Akshit,\n\n")}",
        )
    val intent = Intent(Intent.ACTION_SENDTO, uri)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Write a review to Ashborne"))
    }
}

private fun openGithubProfile(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/joshiakshit"))
    runCatching { context.startActivity(intent) }
}
