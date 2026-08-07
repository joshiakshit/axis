package com.ash.axis.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ash.core.storage.PreferencesStore
import com.ash.core.ui.theme.AppTheme
import com.ash.core.ui.theme.ColorProfiles
import com.ash.core.ui.theme.ThemeMode
import com.ash.core.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var preferencesStore: PreferencesStore

    private val qrScanRequests = MutableStateFlow(0)

    private data class StartupData(
        val themeMode: String,
        val colorProfile: String,
        val accentColor: String,
        val startRoute: String,
    )

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SCAN_QR) qrScanRequests.value += 1
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()
        if (intent?.action == ACTION_SCAN_QR) qrScanRequests.value += 1

        var startup by mutableStateOf<StartupData?>(null)
        splashScreen.setKeepOnScreenCondition { startup == null }
        lifecycleScope.launch {
            startup =
                withContext(Dispatchers.IO) {
                    StartupData(
                        themeMode = preferencesStore.getString("theme_mode", ThemeMode.DARK.name).first(),
                        colorProfile =
                            preferencesStore
                                .getString("color_profile", ColorProfiles.Default.name)
                                .first(),
                        accentColor = preferencesStore.getString("accent_color", "").first(),
                        startRoute = preferencesStore.getString("last_route", "dashboard").first(),
                    )
                }
        }

        val launchedForScan = intent?.action == ACTION_SCAN_QR

        setContent {
            val startupData = startup ?: return@setContent
            val qrScanRequest by qrScanRequests.collectAsStateWithLifecycle()
            // Play the branded splash once per cold start, but skip it when the user tapped the QR shortcut.
            var splashDone by rememberSaveable { mutableStateOf(launchedForScan) }
            val themeModeStr by preferencesStore
                .getString("theme_mode", ThemeMode.DARK.name)
                .collectAsStateWithLifecycle(initialValue = startupData.themeMode)
            val colorProfile by preferencesStore
                .getString("color_profile", ColorProfiles.Default.name)
                .collectAsStateWithLifecycle(initialValue = startupData.colorProfile)
            val accentColor by preferencesStore
                .getString("accent_color", "")
                .collectAsStateWithLifecycle(initialValue = startupData.accentColor)

            val themeState =
                ThemeState(
                    mode = ThemeMode.entries.find { it.name == themeModeStr } ?: ThemeMode.DARK,
                    profileName = colorProfile,
                    accentHex = accentColor,
                )

            AppTheme(themeState = themeState) {
                if (!splashDone) {
                    AxisSplash(onFinished = { splashDone = true })
                } else {
                    SessionGate(
                        preferencesStore = preferencesStore,
                        qrScanRequest = qrScanRequest,
                        startRoute = startupData.startRoute,
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestHighRefreshRate() {
        val display = display ?: window.windowManager.defaultDisplay ?: return
        val bestMode =
            display.supportedModes.maxByOrNull { it.refreshRate } ?: return
        window.attributes =
            window.attributes.apply { preferredDisplayModeId = bestMode.modeId }
    }

    companion object {
        const val ACTION_SCAN_QR = "com.ash.axis.action.SCAN_QR"
    }
}
