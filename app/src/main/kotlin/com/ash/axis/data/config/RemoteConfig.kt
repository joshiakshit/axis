package com.ash.axis.data.config

import kotlinx.serialization.Serializable

// Mirror of the Axis backend's GET /v1/config contract. Every field has a default so a partial or absent
// response still yields a usable config, and a disabled/unreachable backend leaves behaviour unchanged.
@Serializable
data class RemoteConfig(
    // iCloudEMS static bearer. Null/blank -> the app keeps using its compiled-in BuildConfig token.
    val authToken: String? = null,
    // `appversion` string sent to iCloudEMS on OTP calls.
    val appVersion: String = DEFAULT_APP_VERSION,
    // Force-update floor: the app blocks when its BuildConfig.VERSION_CODE is below this.
    val minSupportedVersionCode: Int = 1,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "",
    val updateUrl: String = "",
    // Hard stop: when true the app shows `message` and blocks use.
    val killSwitch: Boolean = false,
    val message: String = "",
    val updatedAt: String = "",
) {
    companion object {
        const val DEFAULT_APP_VERSION = "3.0.3"
    }
}
