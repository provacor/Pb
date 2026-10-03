package com.provacor.sathi.voice

import com.provacor.sathi.agent.AgentController
import com.provacor.sathi.settings.Settings
import com.provacor.sathi.tts.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the microphone loop.
 *
 * - [listenOnce]: one command, then the mic turns off.
 * - [startHandsFree]: listen, run the command, wait for the spoken reply to
 *   finish (so Sathi never hears itself), listen again, until [stop].
 *   Silence just restarts listening.
 */
class VoiceController(
    private val voice: SpeechRecognizerManager,
    private val agent: AgentController,
    private val tts: TextToSpeechManager,
    private val settings: StateFlow<Settings>,
) {
    enum class Mode { OFF, ONCE, HANDS_FREE }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var task: Job? = null
    private var restart: Job? = null
    private var failures = 0

    private val _mode = MutableStateFlow(Mode.OFF)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _partial = MutableStateFlow<String?>(null)
    val partial: StateFlow<String?> = _partial.asStateFlow()

    private val _error = MutableStateFlow<VoiceError?>(null)
    val error: StateFlow<VoiceError?> = _error.asStateFlow()

    val listening: StateFlow<Boolean> get() = voice.listening
    val level: StateFlow<Float> get() = voice.level

    init {
        scope.launch { voice.events.collect(::onEvent) }
    }

    fun listenOnce() {
        if (_mode.value == Mode.HANDS_FREE) return
        _mode.value = Mode.ONCE
        agent.clearTask()
        listen()
    }

    fun startHandsFree() {
        if (_mode.value == Mode.HANDS_FREE) return
        _mode.value = Mode.HANDS_FREE
        failures = 0
        if (!voice.listening.value && task?.isActive != true) listen()
    }

    /** Ends listening. In one-shot mode what was said so far is still used. */
    fun stopListening() {
        if (_mode.value == Mode.ONCE) voice.stop() else stop()
    }

    fun stop() {
        _mode.value = Mode.OFF
        restart?.cancel()
        voice.cancel()
        _partial.value = null
    }

    fun submitText(text: String) {
        if (voice.listening.value) voice.cancel()
        run(text)
    }

    private fun listen() {
        restart?.cancel()
        _error.value = null
        _partial.value = null
        tts.stop()
        val s = settings.value
        voice.start(s.language, s.preferOffline)
    }

    private fun onEvent(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.Partial -> _partial.value = event.text
            is VoiceEvent.Final -> {
                _partial.value = null
                failures = 0
                run(event.text)
            }
            is VoiceEvent.Error -> {
                _partial.value = null
                onError(event.error)
            }
        }
    }

    private fun onError(error: VoiceError) {
        val handsFree = _mode.value == Mode.HANDS_FREE
        when {
            // Silence in hands-free mode is normal: just listen again.
            handsFree && error == VoiceError.NO_SPEECH -> restartAfter(RESTART_MILLIS)
            handsFree && error in RETRYABLE && failures < MAX_FAILURES -> {
                failures++
                restartAfter(RESTART_MILLIS * (failures + 1))
            }
            else -> {
                _error.value = error
                _mode.value = Mode.OFF
            }
        }
    }

    private fun run(text: String) {
        task?.cancel()
        task = scope.launch {
            val s = settings.value
            agent.handle(text, s.language, s.speakReplies)
            if (_mode.value == Mode.HANDS_FREE) {
                awaitSpeechDone()
                if (_mode.value == Mode.HANDS_FREE) listen()
            } else {
                _mode.value = Mode.OFF
            }
        }
    }

    private suspend fun awaitSpeechDone() {
        delay(300)
        withTimeoutOrNull(MAX_REPLY_MILLIS) { tts.state.first { !it.speaking } }
    }

    private fun restartAfter(millis: Long) {
        restart?.cancel()
        restart = scope.launch {
            delay(millis)
            if (_mode.value == Mode.HANDS_FREE && !voice.listening.value) listen()
        }
    }

    private companion object {
        const val RESTART_MILLIS = 400L
        const val MAX_FAILURES = 5
        const val MAX_REPLY_MILLIS = 60_000L
        val RETRYABLE = setOf(VoiceError.BUSY, VoiceError.NETWORK, VoiceError.OTHER)
    }
}
