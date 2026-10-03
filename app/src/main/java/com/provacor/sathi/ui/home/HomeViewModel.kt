package com.provacor.sathi.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.provacor.sathi.agent.AgentState
import com.provacor.sathi.container
import com.provacor.sathi.settings.Settings
import com.provacor.sathi.tts.TtsState
import com.provacor.sathi.voice.ListeningService
import com.provacor.sathi.voice.VoiceController
import com.provacor.sathi.voice.VoiceError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoiceUi(
    val listening: Boolean = false,
    val level: Float = 0f,
    val partial: String? = null,
    val error: VoiceError? = null,
    val mode: VoiceController.Mode = VoiceController.Mode.OFF,
)

data class HomeUiState(
    val agent: AgentState = AgentState(),
    val voice: VoiceUi = VoiceUi(),
    val tts: TtsState = TtsState(),
    val settings: Settings = Settings(),
    val screenControl: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val voice = container.voice

    private val voiceUi = combine(voice.listening, voice.level, voice.partial, voice.error, voice.mode) { l, lv, p, e, m ->
        VoiceUi(l, lv, p, e, m)
    }

    val ui: StateFlow<HomeUiState> =
        combine(container.agent.state, voiceUi, container.tts.state, container.currentSettings, container.screen.connected) { a, v, t, s, sc ->
            HomeUiState(a, v, t, s, sc)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Mic button: one command at a time, or stop when hands-free is running. */
    fun toggleListening() {
        when {
            voice.mode.value == VoiceController.Mode.HANDS_FREE -> setHandsFree(false)
            voice.listening.value -> voice.stopListening()
            else -> voice.listenOnce()
        }
    }

    fun setHandsFree(on: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch { container.settings.setHandsFree(on) }
        if (on) ListeningService.start(app) else ListeningService.stop(app)
    }

    /** Called when the home screen appears: resume hands-free if the user left it on. */
    fun resumeHandsFreeIfWanted(micGranted: Boolean) {
        val s = container.currentSettings.value
        if (s.handsFree && micGranted && voice.mode.value != VoiceController.Mode.HANDS_FREE) {
            ListeningService.start(getApplication())
        }
    }

    fun submit(text: String) = voice.submitText(text)

    fun pauseSpeech() {
        container.tts.pause()
    }

    fun resumeSpeech() {
        container.tts.resume()
    }

    fun stopSpeech() {
        container.tts.stop()
    }
}
