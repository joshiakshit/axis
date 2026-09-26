package com.ash.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager internal constructor(private val prefs: SharedPreferences) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        EncryptedSharedPreferences.create(
            "secure_tokens",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ),
    )

    init {
        migrateIfNeeded()
        retainActiveAccount()
    }

    private fun migrateIfNeeded() {
        if (prefs.getString(KEY_ACTIVE_ADMNO, null) != null) return
        val legacyAccess = prefs.getString(KEY_ACCESS, null) ?: return
        val legacyRefresh = prefs.getString(KEY_REFRESH, null) ?: ""
        val legacyEmail = prefs.getString(KEY_EMAIL, null) ?: ""
        val legacyPhone = prefs.getString(KEY_PHONE, null) ?: ""
        val admno = extractAdmnoFromToken(legacyAccess) ?: return
        prefs.edit()
            .putString(KEY_ACTIVE_ADMNO, admno)
            .putString("${admno}_$KEY_ACCESS", legacyAccess)
            .putString("${admno}_$KEY_REFRESH", legacyRefresh)
            .putString("${admno}_$KEY_EMAIL", legacyEmail)
            .putString("${admno}_$KEY_PHONE", legacyPhone)
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EMAIL)
            .remove(KEY_PHONE)
            .apply()
    }

    private fun extractAdmnoFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload =
                parts[1].let { base64 ->
                    val padded = base64 + "=".repeat((4 - base64.length % 4) % 4)
                    String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
                }
            JSONObject(payload).optString("admno").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun getActiveAdmno(): String? = prefs.getString(KEY_ACTIVE_ADMNO, null)

    fun setActiveAdmno(admno: String) {
        if (getActiveAdmno() != admno) clearCurrentAccount()
        prefs.edit().putString(KEY_ACTIVE_ADMNO, admno).apply()
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
    ) {
        val admno = getActiveAdmno() ?: return
        prefs.edit()
            .putString("${admno}_$KEY_ACCESS", accessToken)
            .putString("${admno}_$KEY_REFRESH", refreshToken)
            .apply()
    }

    fun getAccessToken(): String? {
        val admno = getActiveAdmno() ?: return null
        return prefs.getString("${admno}_$KEY_ACCESS", null)
    }

    fun getRefreshToken(): String? {
        val admno = getActiveAdmno() ?: return null
        return prefs.getString("${admno}_$KEY_REFRESH", null)
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun saveAcademicYear(
        academicYear: String,
        fetchedAt: Long,
    ) {
        val admno = getActiveAdmno() ?: return
        prefs.edit()
            .putString("${admno}_$KEY_ACADEMIC_YEAR", academicYear)
            .putLong("${admno}_$KEY_ACADEMIC_YEAR_FETCHED_AT", fetchedAt)
            .apply()
    }

    fun getAcademicYear(): String? {
        val admno = getActiveAdmno() ?: return null
        return prefs.getString("${admno}_$KEY_ACADEMIC_YEAR", null)
    }

    fun getAcademicYearFetchedAt(): Long {
        val admno = getActiveAdmno() ?: return 0L
        return prefs.getLong("${admno}_$KEY_ACADEMIC_YEAR_FETCHED_AT", 0L)
    }

    fun saveUserMeta(
        email: String,
        phone: String,
    ) {
        val admno = getActiveAdmno() ?: return
        prefs.edit()
            .putString("${admno}_$KEY_EMAIL", email)
            .putString("${admno}_$KEY_PHONE", phone)
            .apply()
    }

    fun getEmail(): String? {
        val admno = getActiveAdmno() ?: return null
        return prefs.getString("${admno}_$KEY_EMAIL", null)
    }

    fun getPhone(): String? {
        val admno = getActiveAdmno() ?: return null
        return prefs.getString("${admno}_$KEY_PHONE", null)
    }

    private fun retainActiveAccount() {
        val active = getActiveAdmno()
        val editor = prefs.edit().remove("account_list")
        prefs.all.keys.filter { key ->
            key != KEY_ACTIVE_ADMNO && key != KEY_DEVICE_ID &&
                (active == null || !key.startsWith("${active}_"))
        }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun clearCurrentAccount() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it != KEY_DEVICE_ID }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun hasTokens(): Boolean = getAccessToken() != null

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_ACADEMIC_YEAR = "academic_year"
        private const val KEY_ACADEMIC_YEAR_FETCHED_AT = "academic_year_fetched_at"
        private const val KEY_ACTIVE_ADMNO = "active_admno"
    }
}
