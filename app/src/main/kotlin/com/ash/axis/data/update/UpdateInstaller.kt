package com.ash.axis.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.ash.axis.BuildConfig
import com.ash.axis.data.config.RemoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

// Progress of an in-app update, surfaced to the update UI.
data class UpdateState(
    val downloading: Boolean = false,
    // 0..1 when the download size is known, -1 = indeterminate.
    val progress: Float = -1f,
    val committing: Boolean = false,
    val error: String? = null,
)

// True one-tap update: downloads the APK named by remote config and hands it to the system PackageInstaller,
// which shows the standard confirm dialog and swaps the app in place — no browser, no manual "open the file".
// Hosting-agnostic: `updateUrl` can be the Axis Worker's /v1/apk or any direct .apk link.
@Singleton
class UpdateInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
    ) {
        private val mutableState = MutableStateFlow(UpdateState())
        val state: StateFlow<UpdateState> = mutableState.asStateFlow()

        // An update exists when the backend advertises a newer build than the one installed and gives us a link.
        fun updateAvailable(config: RemoteConfig): Boolean =
            config.latestVersionCode > BuildConfig.VERSION_CODE && config.updateUrl.isNotBlank()

        // Android blocks silent installs: the user must have granted this app "install unknown apps".
        fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

        fun requestInstallPermission() {
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun downloadAndInstall(url: String) {
            if (mutableState.value.downloading) return
            if (!canInstall()) {
                requestInstallPermission()
                return
            }
            mutableState.update { UpdateState(downloading = true) }
            try {
                withContext(Dispatchers.IO) { stream(url) }
                mutableState.update { it.copy(downloading = false, committing = true) }
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
                mutableState.update { UpdateState(error = "Update failed. Please try again.") }
            }
        }

        fun clearError() = mutableState.update { it.copy(error = null) }

        // Download the APK and hand it to the system. The commit result comes back asynchronously to
        // InstallReceiver, which launches the system confirm dialog.
        private fun stream(url: String) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body ?: error("empty response")
                if (!response.isSuccessful) error("http ${response.code}")
                install(body, body.contentLength())
            }
        }

        // Stream `body` straight into a PackageInstaller session, then commit it.
        private fun install(
            body: ResponseBody,
            total: Long,
        ) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (total > 0) params.setSize(total)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("axis", 0, total).use { out ->
                    pump(body, out, total)
                    session.fsync(out)
                }
                session.commit(InstallReceiver.statusSender(context, sessionId))
            }
        }

        // Copy bytes, publishing download progress when the total size is known.
        private fun pump(
            body: ResponseBody,
            out: OutputStream,
            total: Long,
        ) {
            val buf = ByteArray(BUFFER)
            var written = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    out.write(buf, 0, read)
                    written += read
                    if (total > 0) mutableState.update { it.copy(progress = written.toFloat() / total) }
                }
            }
        }

        private companion object {
            const val TAG = "UpdateInstaller"
            const val BUFFER = 64 * 1024
        }
    }
