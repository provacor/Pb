package com.provacor.sathi.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.provacor.sathi.agent.Speaker
import com.provacor.sathi.core.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class TtsState(
    val ready: Boolean = false,
    val speaking: Boolean = false,
    val paused: Boolean = false,
    /** Languages the installed engine has no voice for. */
    val missingVoices: Set<Language> = emptySet(),
)

/**
 * Speaks replies in Bengali or English.
 *
 * Android's engine has no real pause, so text is spoken sentence by sentence:
 * [pause] stops after noting the current sentence and [resume] continues from it.
 */
class TextToSpeechManager(context: Context) : Speaker {

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var pending: Pair<String, Language>? = null

    @Volatile private var chunks: List<String> = emptyList()
    @Volatile private var chunkIndex = 0
    @Volatile private var chunkLanguage = Language.BENGALI
    @Volatile private var session = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(progress)
                _state.update { it.copy(ready = true, missingVoices = findMissingVoices()) }
                pending?.let { (text, lang) -> pending = null; speak(text, lang) }
            } else {
                Log.w(TAG, "TextToSpeech init failed: $status")
            }
        }
    }

    override fun speak(text: String, language: Language) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !_state.value.ready) {
            pending = text to language
            return
        }
        session++
        engine.stop()
        chunks = split(text)
        chunkIndex = 0
        chunkLanguage = language
        applyLanguage(engine, language)
        enqueueFrom(0)
    }

    fun pause() {
        if (!_state.value.speaking) return
        session++
        tts?.stop()
        _state.update { it.copy(speaking = false, paused = true) }
    }

    fun resume() {
        if (!_state.value.paused) return
        val engine = tts ?: return
        session++
        applyLanguage(engine, chunkLanguage)
        enqueueFrom(chunkIndex)
    }

    override fun stop() {
        session++
        pending = null
        tts?.stop()
        chunks = emptyList()
        _state.update { it.copy(speaking = false, paused = false) }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    fun refreshVoices() {
        if (_state.value.ready) _state.update { it.copy(missingVoices = findMissingVoices()) }
    }

    private fun enqueueFrom(start: Int) {
        val engine = tts ?: return
        val current = chunks
        if (start >= current.size) return
        _state.update { it.copy(speaking = true, paused = false) }
        for (i in start until current.size) {
            engine.speak(current[i], TextToSpeech.QUEUE_ADD, null, "s$session-$i")
        }
    }

    private fun applyLanguage(engine: TextToSpeech, language: Language) {
        for (locale in locales(language)) {
            val result = engine.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) return
        }
    }

    private fun findMissingVoices(): Set<Language> {
        val engine = tts ?: return emptySet()
        return Language.entries.filter { lang ->
            locales(lang).none { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        }.toSet()
    }

    private fun locales(language: Language): List<Locale> = when (language) {
        Language.BENGALI -> listOf(Locale.forLanguageTag("bn-BD"), Locale.forLanguageTag("bn-IN"), Locale.forLanguageTag("bn"))
        Language.ENGLISH -> listOf(Locale.US, Locale.UK, Locale.ENGLISH)
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (s, i) = parse(utteranceId) ?: return
            if (s == session) chunkIndex = i
        }

        override fun onDone(utteranceId: String?) {
            val (s, i) = parse(utteranceId) ?: return
            if (s != session) return
            chunkIndex = i + 1
            if (i >= chunks.lastIndex) _state.update { it.copy(speaking = false, paused = false) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val (s, _) = parse(utteranceId) ?: return
            if (s == session) _state.update { it.copy(speaking = false, paused = false) }
        }

        private fun parse(id: String?): Pair<Int, Int>? {
            if (id == null || !id.startsWith("s")) return null
            val parts = id.drop(1).split('-')
            val s = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val i = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return s to i
        }
    }

    companion object {
        private const val TAG = "TextToSpeech"
        private val sentenceEnd = Regex("(?<=[।.!?\\n])\\s+")

        internal fun split(text: String): List<String> =
            text.split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }

        /** Opens the engine's screen for downloading voice data (e.g. Bengali). */
        fun installVoiceDataIntent(): Intent =
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
