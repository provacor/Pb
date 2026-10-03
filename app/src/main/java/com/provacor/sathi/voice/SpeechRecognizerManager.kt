package com.provacor.sathi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.provacor.sathi.core.model.Language
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceError { NO_SPEECH, NETWORK, PERMISSION, BUSY, UNAVAILABLE, LANGUAGE, OTHER }

sealed interface VoiceEvent {
    data class Partial(val text: String) : VoiceEvent
    data class Final(val text: String) : VoiceEvent
    data class Error(val error: VoiceError) : VoiceEvent
}

/**
 * Wraps Android's [SpeechRecognizer]. Listens only between [start] and the
 * end of one utterance; never in the background. Must be used from the main thread.
 */
class SpeechRecognizerManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    /** Input loudness, 0..1, for the microphone animation. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: Language, preferOffline: Boolean) {
        if (!isAvailable()) {
            _events.tryEmit(VoiceEvent.Error(VoiceError.UNAVAILABLE))
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        }
        _level.value = 0f
        _listening.value = true
        try {
            r.startListening(intent)
        } catch (e: SecurityException) {
            Log.w(TAG, "startListening refused", e)
            finish(VoiceEvent.Error(VoiceError.PERMISSION))
        }
    }

    /** Stop and deliver what was heard so far. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Stop and throw away what was heard. */
    fun cancel() {
        recognizer?.cancel()
        _listening.value = false
        _level.value = 0f
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _listening.value = false
    }

    private fun finish(event: VoiceEvent) {
        _listening.value = false
        _level.value = 0f
        _events.tryEmit(event)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            _level.value = 0f
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // Typical range is about -2 dB (silence) to 10 dB (loud speech).
            _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.bestResult() ?: return
            _events.tryEmit(VoiceEvent.Partial(text))
        }

        override fun onResults(results: Bundle?) {
            val text = results.bestResult()
            finish(if (text.isNullOrBlank()) VoiceEvent.Error(VoiceError.NO_SPEECH) else VoiceEvent.Final(text))
        }

        override fun onError(error: Int) {
            finish(VoiceEvent.Error(mapError(error)))
        }
    }

    private fun Bundle?.bestResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun mapError(code: Int): VoiceError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NO_SPEECH
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> VoiceError.NETWORK
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.BUSY
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> VoiceError.LANGUAGE
        else -> VoiceError.OTHER
    }

    private companion object {
        const val TAG = "SpeechRecognizer"

        // SpeechRecognizer.ERROR_LANGUAGE_* were added in API 31; the values are stable.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
