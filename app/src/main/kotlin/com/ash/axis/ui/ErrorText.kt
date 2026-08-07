package com.ash.axis.ui

import com.ash.axis.data.repository.IcloudServerException
import com.ash.axis.data.repository.SessionExpiredException
import retrofit2.HttpException
import java.io.IOException

object ErrorText {
    const val SERVER_DOWN =
        "Galgotias' iCloudEMS server isn't responding right now. This is on their end, not yours — please try again in a few minutes."
    const val OFFLINE =
        "You appear to be offline. Check your internet connection and try again."
    const val SESSION_EXPIRED =
        "Your session has expired. Please log in again."
    const val BLOCKED =
        "Galgotias' server temporarily refused this request. This usually clears up on its own — try again in a few minutes."
    const val DEVICE_LOCKED =
        "This account is currently signed in on another device. Log in again here to move it to this device."
    private const val GENERIC = "Something went wrong. Please try again."

    fun forData(t: Throwable): String =
        when {
            isDeviceLocked(t) -> DEVICE_LOCKED
            isBlocked(t) -> BLOCKED
            isServerDown(t) -> SERVER_DOWN
            t is SessionExpiredException -> SESSION_EXPIRED
            t is HttpException && t.code() == 401 -> SESSION_EXPIRED
            isNetwork(t) -> OFFLINE
            else -> t.message?.takeIf { it.isNotBlank() } ?: GENERIC
        }

    fun forLogin(
        t: Throwable,
        otpStep: Boolean,
    ): String =
        when {
            isDeviceLocked(t) -> DEVICE_LOCKED
            isServerDown(t) -> SERVER_DOWN
            isNetwork(t) -> OFFLINE
            t is HttpException && t.code() in 400..499 ->
                if (otpStep) {
                    "That OTP looks incorrect or expired. Request a new one and try again."
                } else {
                    "We couldn't find a Galgotias account for that number. Double-check it's your registered phone number."
                }
            else -> t.message?.takeIf { it.isNotBlank() } ?: GENERIC
        }

    // Detect the server's "already linked to another device" one-device-per-account lock so we can
    // tell the user exactly what to do (re-logging in here re-claims the account for this device).
    fun isDeviceLocked(t: Throwable): Boolean {
        val msg = t.message ?: return false
        return msg.contains("another device", ignoreCase = true) ||
            msg.contains("already linked", ignoreCase = true)
    }

    private fun isBlocked(t: Throwable): Boolean =
        (t is IcloudServerException && t.statusCode == 403) || (t is HttpException && t.code() == 403)

    private fun isServerDown(t: Throwable): Boolean = t is IcloudServerException || (t is HttpException && t.code() in 500..599)

    private fun isNetwork(t: Throwable): Boolean = t is IOException
}
