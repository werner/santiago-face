package com.example.santiagoface.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentSpokenWord = MutableStateFlow("")
    val currentSpokenWord: StateFlow<String> = _currentSpokenWord.asStateFlow()

    private val _mouthOpening = MutableStateFlow(0f)
    val mouthOpening: StateFlow<Float> = _mouthOpening.asStateFlow()

    private var currentTextToSpeak: String = ""
    private val networkFallbackPolicy = NetworkFallbackPolicy()

    private var onStartCallback: (() -> Unit)? = null
    private var onDoneCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    init {
        setupUtteranceListener()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            // Default pitch and speech rate set to natural 1.0f for authentic human pronunciation
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)
            configureLanguageAndVoiceForText("Hola")
        } else {
            Log.e("TtsManager", "TTS Initialization failed with status: $status")
        }
    }

    private fun configureLanguageAndVoiceForText(
        text: String,
        forceOffline: Boolean = networkFallbackPolicy.useOfflineFallback
    ) {
        if (tts == null) return

        val isSpanish = isSpanishText(text)
        val targetLocale = if (isSpanish) {
            val esLocales = listOf(
                Locale.forLanguageTag("es-ES"),
                Locale.forLanguageTag("es-MX"),
                Locale.forLanguageTag("es-US"),
                Locale.forLanguageTag("es")
            )
            var chosenLocale = Locale.forLanguageTag("es-ES")
            for (loc in esLocales) {
                val res = tts?.setLanguage(loc)
                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                    chosenLocale = loc
                    break
                }
            }
            chosenLocale
        } else {
            val enLocale = Locale.US
            tts?.setLanguage(enLocale)
            enLocale
        }

        try {
            val voices = tts?.voices
            if (!voices.isNullOrEmpty()) {
                val targetLang = targetLocale.language
                val bestVoice = voices.asSequence()
                    .filter { voice ->
                        voice.locale.language == targetLang &&
                        !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) &&
                        (!forceOffline || !voice.isNetworkConnectionRequired)
                    }
                    .sortedWith(
                        compareByDescending<android.speech.tts.Voice> { it.quality }
                            .thenBy { if (!it.isNetworkConnectionRequired) 1 else 0 }
                    )
                    .firstOrNull()

                if (bestVoice != null) {
                    tts?.voice = bestVoice
                    Log.d("TtsManager", "Selected native voice: ${bestVoice.name} (offline=${!bestVoice.isNetworkConnectionRequired}) for language ${targetLocale.language}")
                }
            }
        } catch (e: Exception) {
            Log.w("TtsManager", "Could not set specific TTS voice: ${e.message}")
        }
    }

    fun isSpanishText(text: String): Boolean = SpanishLanguageUtils.isSpanishText(text)

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _mouthOpening.value = 0.5f
                onStartCallback?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                networkFallbackPolicy.onSuccess()
                _isSpeaking.value = false
                _mouthOpening.value = 0f
                _currentSpokenWord.value = ""
                onDoneCallback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _mouthOpening.value = 0f
                _currentSpokenWord.value = ""
                onErrorCallback?.invoke("TTS Error for utterance $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val isNetworkError =
                    errorCode == TextToSpeech.ERROR_NETWORK || errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT

                if (currentTextToSpeak.isNotEmpty()) {
                    when (networkFallbackPolicy.onError(isNetworkError)) {
                        NetworkFallbackPolicy.FailureAction.RETRY_ONLINE -> {
                            Log.w("TtsManager", "TTS network error ($errorCode); retrying online.")
                            enqueueCurrentUtterance()
                            return
                        }
                        NetworkFallbackPolicy.FailureAction.SWITCH_TO_OFFLINE -> {
                            Log.w("TtsManager", "TTS network error ($errorCode) persisted; switching to local offline voice.")
                            configureLanguageAndVoiceForText(currentTextToSpeak, forceOffline = true)
                            enqueueCurrentUtterance()
                            return
                        }
                        NetworkFallbackPolicy.FailureAction.REPORT_ERROR -> Unit
                    }
                }

                _isSpeaking.value = false
                _mouthOpening.value = 0f
                _currentSpokenWord.value = ""
                onErrorCallback?.invoke("TTS Error code $errorCode")
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val text = currentTextToSpeak
                if (text.isNotEmpty() && start in 0..text.length && end in start..text.length) {
                    val word = text.substring(start, end).trim()
                    _currentSpokenWord.value = word
                    _mouthOpening.value = calculateMouthOpening(word)
                }
            }
        })
    }

    private fun calculateMouthOpening(word: String): Float {
        if (word.isEmpty()) return 0.2f
        val clean = word.lowercase(Locale.ROOT)
        var maxOpening = 0.3f
        for (char in clean) {
            val opening = when (char) {
                'a', 'o', 'u', '0', 'á', 'ó', 'ú' -> 1.0f
                'e', 'i', 'y', '3', '8', 'é', 'í' -> 0.75f
                'w', 'q', '4' -> 0.85f
                'm', 'b', 'p' -> 0.15f
                'f', 'v', 's', 'z' -> 0.4f
                't', 'd', 'k', 'g', 'n', 'l', 'r', 'ñ' -> 0.55f
                else -> 0.35f
            }
            if (opening > maxOpening) {
                maxOpening = opening
            }
        }
        return maxOpening
    }

    fun setPitchAndRate(pitch: Float, rate: Float) {
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)
    }

    fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            onError?.invoke("TTS engine not initialized")
            return
        }

        networkFallbackPolicy.refreshForNewOperation()
        configureLanguageAndVoiceForText(text)

        currentTextToSpeak = text
        _currentSpokenWord.value = ""
        _mouthOpening.value = 0.5f

        this.onStartCallback = onStart
        this.onDoneCallback = onDone
        this.onErrorCallback = onError

        enqueueCurrentUtterance()
    }

    private fun enqueueCurrentUtterance() {
        val text = currentTextToSpeak
        if (text.isEmpty() || tts == null) return

        val utteranceId = "Santiago_Utterance_${System.currentTimeMillis()}"
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId

        @Suppress("DEPRECATION")
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)

        if (result == TextToSpeech.ERROR) {
            _isSpeaking.value = false
            _mouthOpening.value = 0f
            _currentSpokenWord.value = ""
            onErrorCallback?.invoke("Failed to enqueue TTS speech")
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
        _isSpeaking.value = false
        _mouthOpening.value = 0f
        _currentSpokenWord.value = ""
    }

    fun shutdown() {
        networkFallbackPolicy.reset()
        if (isInitialized) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
        _isSpeaking.value = false
        _mouthOpening.value = 0f
        _currentSpokenWord.value = ""
    }
}
