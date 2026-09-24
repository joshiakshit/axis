package com.ash.axis.data.config

import kotlinx.serialization.Serializable

@Serializable
data class RemoteConfig(
    // Use the compiled-in token when no override is set.
    val authToken: String? = null,
    // iCloudEMS OTP appversion, separate from the Axis app version.
    val appVersion: String = DEFAULT_APP_VERSION,
    // Block app versions below this code.
    val minSupportedVersionCode: Int = 1,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "",
    val updateUrl: String = "",
    val killSwitch: Boolean = false,
    val message: String = "",
    val notice: String = "",
    // Comma-separated admission number prefixes. Blank disables auto-approval.
    val autoApprovePrefix: String = "",
    val updatedAt: String = "",
) {
    companion object {
        const val DEFAULT_APP_VERSION = "3.0.9"
    }
}
