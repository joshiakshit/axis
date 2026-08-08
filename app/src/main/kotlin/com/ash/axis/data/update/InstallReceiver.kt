package com.ash.axis.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

// Receives the PackageInstaller commit result. The one step that matters: when the installer needs the user
// to confirm (STATUS_PENDING_USER_ACTION), it hands us the system confirm Intent — we must launch it, or the
// install silently stalls. Success/failure are just logged (on success the app process is replaced anyway).
class InstallReceiver : BroadcastReceiver() {
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
            else -> Log.w(TAG, "install status=$status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }

    companion object {
        private const val TAG = "InstallReceiver"
        private const val ACTION = "com.ash.axis.action.INSTALL_STATUS"

        // A mutable IntentSender targeting this receiver, keyed by session so concurrent commits don't collide.
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
