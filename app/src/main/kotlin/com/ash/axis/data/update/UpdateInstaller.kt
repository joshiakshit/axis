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

data class UpdateState(
    val stage: UpdateStage = UpdateStage.IDLE,
    // 0..1 when the download size is known; -1 otherwise.
    val progress: Float = -1f,
    val error: String? = null,
)

enum class UpdateStage {
    IDLE,
    DOWNLOADING,
    OPENING_INSTALLER,
}

@Singleton
class UpdateInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
    ) {
        private val mutableState = MutableStateFlow(UpdateState())
        val state: StateFlow<UpdateState> = mutableState.asStateFlow()
        private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        fun updateAvailable(config: RemoteConfig): Boolean =
            config.latestVersionCode > BuildConfig.VERSION_CODE && config.updateUrl.isNotBlank()

        fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

        fun requestInstallPermission() {
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun downloadAndInstall(url: String) {
            if (mutableState.value.stage != UpdateStage.IDLE) return
            if (!canInstall()) {
                requestInstallPermission()
                return
            }
            mutableState.update { UpdateState(stage = UpdateStage.DOWNLOADING) }
            try {
                withContext(Dispatchers.IO) { stream(url) }
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
                mutableState.update { UpdateState(error = "Update failed. Please try again.") }
            }
        }

        fun onInstallFailed(cancelled: Boolean) {
            preferences.edit().remove(KEY_UPDATE_FROM).apply()
            val message = if (cancelled) "Installation was cancelled." else "Installation failed. Please try again."
            mutableState.value = UpdateState(error = message)
        }

        fun consumeCompletedVersion(): String? {
            val previousVersion = preferences.getInt(KEY_UPDATE_FROM, -1)
            if (previousVersion < 0 || BuildConfig.VERSION_CODE <= previousVersion) return null
            preferences.edit().remove(KEY_UPDATE_FROM).apply()
            return BuildConfig.VERSION_NAME
        }

        // InstallReceiver handles the commit result and opens the system confirmation.
        private fun stream(url: String) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body ?: error("empty response")
                if (!response.isSuccessful) error("http ${response.code}")
                install(body, body.contentLength())
            }
        }

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
                mutableState.update { it.copy(stage = UpdateStage.OPENING_INSTALLER, progress = 1f) }
                preferences.edit().putInt(KEY_UPDATE_FROM, BuildConfig.VERSION_CODE).commit()
                session.commit(InstallReceiver.statusSender(context, sessionId))
            }
        }

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
            const val PREFERENCES = "axis_updates"
            const val KEY_UPDATE_FROM = "update_from_version"
        }
    }
