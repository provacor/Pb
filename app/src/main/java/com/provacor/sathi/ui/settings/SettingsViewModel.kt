package com.provacor.sathi.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.provacor.sathi.container
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.settings.Settings
import com.provacor.sathi.tts.TtsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.container.settings
    private val tts = application.container.tts

    /** Null until the stored settings have been read once. */
    val settings: StateFlow<Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val ttsState: StateFlow<TtsState> = tts.state

    fun setLanguage(language: Language) {
        viewModelScope.launch { repo.setLanguage(language) }
    }
    fun setSpeakReplies(on: Boolean) {
        viewModelScope.launch { repo.setSpeakReplies(on) }
    }
    fun setPreferOffline(on: Boolean) {
        viewModelScope.launch { repo.setPreferOffline(on) }
    }
    fun setShowLog(on: Boolean) {
        viewModelScope.launch { repo.setShowLog(on) }
    }
    fun setOnboardingDone() {
        viewModelScope.launch { repo.setOnboardingDone() }
    }

    fun refreshVoices() {
        tts.refreshVoices()
    }

    fun testVoice() {
        val language = settings.value?.language ?: Language.BENGALI
        val text = if (language == Language.BENGALI) "হ্যালো, আমি সাথী। আপনার কমান্ডের অপেক্ষায় আছি।"
        else "Hello, I'm Sathi. Ready for your command."
        tts.speak(text, language)
    }
}
