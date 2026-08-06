package com.ash.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ash.core.security.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class PreferencesStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tokenManager: TokenManager,
    ) {
        fun userScoped(key: String): String {
            val admno = tokenManager.getActiveAdmno() ?: ""
            return if (admno.isBlank()) key else "${admno}_$key"
        }

        fun getString(
            key: String,
            default: String = "",
        ): Flow<String> = context.dataStore.data.map { it[stringPreferencesKey(key)] ?: default }

        suspend fun putString(
            key: String,
            value: String,
        ) {
            context.dataStore.edit { it[stringPreferencesKey(key)] = value }
        }

        fun getBoolean(
            key: String,
            default: Boolean = false,
        ): Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey(key)] ?: default }

        suspend fun putBoolean(
            key: String,
            value: Boolean,
        ) {
            context.dataStore.edit { it[booleanPreferencesKey(key)] = value }
        }

        fun getInt(
            key: String,
            default: Int = 0,
        ): Flow<Int> = context.dataStore.data.map { it[intPreferencesKey(key)] ?: default }

        suspend fun putInt(
            key: String,
            value: Int,
        ) {
            context.dataStore.edit { it[intPreferencesKey(key)] = value }
        }

        fun getUserString(
            key: String,
            default: String = "",
        ): Flow<String> = getString(userScoped(key), default)

        suspend fun putUserString(
            key: String,
            value: String,
        ) {
            putString(userScoped(key), value)
        }

        fun getUserBoolean(
            key: String,
            default: Boolean = false,
        ): Flow<Boolean> = getBoolean(userScoped(key), default)

        suspend fun putUserBoolean(
            key: String,
            value: Boolean,
        ) {
            putBoolean(userScoped(key), value)
        }

        fun getUserInt(
            key: String,
            default: Int = 0,
        ): Flow<Int> = getInt(userScoped(key), default)

        suspend fun putUserInt(
            key: String,
            value: Int,
        ) {
            putInt(userScoped(key), value)
        }

        suspend fun clear() {
            context.dataStore.edit { it.clear() }
        }

        suspend fun clearUserScoped() {
            val prefix = tokenManager.getActiveAdmno()?.let { "${it}_" } ?: return
            context.dataStore.edit { prefs ->
                prefs.asMap().keys
                    .filter { it.name.startsWith(prefix) }
                    .forEach { prefs.remove(it) }
            }
        }
    }
