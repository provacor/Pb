package com.provacor.sathi.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.provacor.sathi.core.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            language = Language.fromTag(p[LANGUAGE]),
            speakReplies = p[SPEAK] ?: true,
            preferOffline = p[OFFLINE] ?: false,
            showLog = p[SHOW_LOG] ?: true,
            onboardingDone = p[ONBOARDED] ?: false,
            handsFree = p[HANDS_FREE] ?: false,
        )
    }

    suspend fun setLanguage(language: Language) = context.dataStore.edit { it[LANGUAGE] = language.name }
    suspend fun setSpeakReplies(on: Boolean) = context.dataStore.edit { it[SPEAK] = on }
    suspend fun setPreferOffline(on: Boolean) = context.dataStore.edit { it[OFFLINE] = on }
    suspend fun setShowLog(on: Boolean) = context.dataStore.edit { it[SHOW_LOG] = on }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[ONBOARDED] = true }
    suspend fun setHandsFree(on: Boolean) = context.dataStore.edit { it[HANDS_FREE] = on }

    private companion object {
        val LANGUAGE = stringPreferencesKey("language")
        val SPEAK = booleanPreferencesKey("speak_replies")
        val OFFLINE = booleanPreferencesKey("prefer_offline")
        val SHOW_LOG = booleanPreferencesKey("show_log")
        val ONBOARDED = booleanPreferencesKey("onboarding_done")
        val HANDS_FREE = booleanPreferencesKey("hands_free")
    }
}
