package com.ash.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AccountEntry(
    val admno: String,
    val name: String,
    val email: String,
)

@Singleton
class TokenManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs: SharedPreferences =
            EncryptedSharedPreferences.create(
                "secure_tokens",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        init {
            migrateIfNeeded()
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

        fun addAccount(
            admno: String,
            name: String,
            email: String,
        ) {
            val accounts = getAccountList().toMutableList()
            accounts.removeAll { it.admno == admno }
            accounts.add(AccountEntry(admno = admno, name = name, email = email))
            saveAccountList(accounts)
        }

        fun removeAccount(admno: String) {
            val accounts = getAccountList().filterNot { it.admno == admno }
            saveAccountList(accounts)
            prefs.edit()
                .remove("${admno}_$KEY_ACCESS")
                .remove("${admno}_$KEY_REFRESH")
                .remove("${admno}_$KEY_EMAIL")
                .remove("${admno}_$KEY_PHONE")
                .apply()
            if (getActiveAdmno() == admno) {
                val next = accounts.firstOrNull()?.admno
                if (next != null) {
                    prefs.edit().putString(KEY_ACTIVE_ADMNO, next).apply()
                } else {
                    prefs.edit().remove(KEY_ACTIVE_ADMNO).apply()
                }
            }
        }

        fun getAccountList(): List<AccountEntry> {
            val raw = prefs.getString(KEY_ACCOUNT_LIST, null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    AccountEntry(
                        admno = obj.getString("admno"),
                        name = obj.optString("name", ""),
                        email = obj.optString("email", ""),
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun saveAccountList(accounts: List<AccountEntry>) {
            val arr = JSONArray()
            accounts.forEach { entry ->
                arr.put(
                    JSONObject().apply {
                        put("admno", entry.admno)
                        put("name", entry.name)
                        put("email", entry.email)
                    },
                )
            }
            prefs.edit().putString(KEY_ACCOUNT_LIST, arr.toString()).apply()
        }

        fun switchTo(admno: String): Boolean {
            val accounts = getAccountList()
            if (accounts.none { it.admno == admno }) return false
            prefs.edit().putString(KEY_ACTIVE_ADMNO, admno).apply()
            return true
        }

        fun clearAll() {
            prefs.edit().clear().apply()
        }

        fun clearCurrentAccount() {
            val admno = getActiveAdmno() ?: return
            removeAccount(admno)
        }

        fun hasTokens(): Boolean = getAccessToken() != null

        companion object {
            private const val KEY_ACCESS = "access_token"
            private const val KEY_REFRESH = "refresh_token"
            private const val KEY_DEVICE_ID = "device_id"
            private const val KEY_EMAIL = "email"
            private const val KEY_PHONE = "phone"
            private const val KEY_ACTIVE_ADMNO = "active_admno"
            private const val KEY_ACCOUNT_LIST = "account_list"
        }
    }
