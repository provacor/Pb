package com.provacor.sathi.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.provacor.sathi.agent.AgentState
import com.provacor.sathi.container
import com.provacor.sathi.settings.Settings
import com.provacor.sathi.tts.TtsState
import com.provacor.sathi.voice.SpeechRecognizerManager
import com.provacor.sathi.voice.VoiceError
import com.provacor.sathi.voice.VoiceEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
)

data class HomeUiState(
    val agent: AgentState = AgentState(),
    val voice: VoiceUi = VoiceUi(),
    val tts: TtsState = TtsState(),
    val settings: Settings = Settings(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val voice = SpeechRecognizerManager(application)
    private val partial = MutableStateFlow<String?>(null)
    private val voiceError = MutableStateFlow<VoiceError?>(null)
    private var task: Job? = null

    private val settings: StateFlow<Settings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val voiceUi = combine(voice.listening, voice.level, partial, voiceError) { listening, level, p, e ->
        VoiceUi(listening, level, p, e)
    }

    val ui: StateFlow<HomeUiState> =
        combine(container.agent.state, voiceUi, container.tts.state, settings) { agent, v, tts, s ->
            HomeUiState(agent, v, tts, s)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            voice.events.collect { event ->
                when (event) {
                    is VoiceEvent.Partial -> partial.value = event.text
                    is VoiceEvent.Final -> {
                        partial.value = null
                        run(event.text)
                    }
                    is VoiceEvent.Error -> {
                        partial.value = null
                        voiceError.value = event.error
                    }
                }
            }
        }
    }

    fun toggleListening() {
        if (voice.listening.value) {
            voice.stop()
            return
        }
        voiceError.value = null
        partial.value = null
        container.tts.stop()
        container.agent.clearTask()
        val s = settings.value
        voice.start(s.language, s.preferOffline)
    }

    fun submit(text: String) {
        if (voice.listening.value) voice.cancel()
        run(text)
    }

    fun pauseSpeech() {
        container.tts.pause()
    }

    fun resumeSpeech() {
        container.tts.resume()
    }

    fun stopSpeech() {
        container.tts.stop()
    }

    private fun run(text: String) {
        voiceError.value = null
        task?.cancel()
        val s = settings.value
        task = viewModelScope.launch { container.agent.handle(text, s.language, s.speakReplies) }
    }

    override fun onCleared() {
        voice.destroy()
    }
}
