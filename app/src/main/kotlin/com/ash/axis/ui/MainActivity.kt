package com.ash.axis.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ash.axis.data.config.RemoteConfigRepository
import com.ash.axis.data.session.AxisSessionRepository
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

    @Inject lateinit var remoteConfig: RemoteConfigRepository

    @Inject lateinit var axisSession: AxisSessionRepository

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
        if (intent?.action == ACTION_SCAN_QR) qrScanRequests.value += 1

        var startup by mutableStateOf<StartupData?>(null)
        splashScreen.setKeepOnScreenCondition { startup == null }
        val startupJob =
            lifecycleScope.launch {
                // Load cached overrides before the first request, then refresh in the background.
                withContext(Dispatchers.IO) {
                    remoteConfig.hydrate()
                    axisSession.hydrate()
                }
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                startupJob.join()
                remoteConfig.refresh()
            }
        }

        val launchedForScan = intent?.action == ACTION_SCAN_QR

        setContent {
            val startupData = startup ?: return@setContent
            val qrScanRequest by qrScanRequests.collectAsStateWithLifecycle()
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

            // Status bar icons must follow the app theme, which can differ from the system theme.
            val darkTheme =
                when (themeState.mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
            }

            AppTheme(themeState = themeState) {
                if (!splashDone) {
                    AxisSplash(onFinished = { splashDone = true })
                } else {
                    RemoteConfigGate(remoteConfig = remoteConfig) {
                        SessionGate(
                            preferencesStore = preferencesStore,
                            qrScanRequest = qrScanRequest,
                            startRoute = startupData.startRoute,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SCAN_QR = "com.ash.axis.action.SCAN_QR"
    }
}
