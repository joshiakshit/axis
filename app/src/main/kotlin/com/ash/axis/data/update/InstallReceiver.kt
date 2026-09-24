package com.ash.axis.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Launch the confirmation intent for STATUS_PENDING_USER_ACTION or installation stalls.
@AndroidEntryPoint
class InstallReceiver : BroadcastReceiver() {
    @Inject
    lateinit var updateInstaller: UpdateInstaller

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }

            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "update installed")
            else -> {
                Log.w(TAG, "install status=$status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                updateInstaller.onInstallFailed(cancelled = status == PackageInstaller.STATUS_FAILURE_ABORTED)
            }
        }
    }

    companion object {
        private const val TAG = "InstallReceiver"
        private const val ACTION = "com.ash.axis.action.INSTALL_STATUS"

        // Use a mutable intent for installer extras and a session ID to separate callbacks.
        fun statusSender(
            context: Context,
            sessionId: Int,
        ): IntentSender {
            val intent = Intent(ACTION).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
        }
    }
}
