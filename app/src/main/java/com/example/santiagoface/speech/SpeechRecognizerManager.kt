package com.example.santiagoface.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechRecognizerManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var onResultListener: ((String) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    private var preferredSpanishLocaleTag: String = "es-ES"

    init {
        initRecognizer()
    }

    fun updateSpanishLocale(localeTag: String) {
        preferredSpanishLocaleTag = if (localeTag.isNotBlank()) localeTag else "es-ES"
    }

    private fun initRecognizer() {
        destroy()

        val appContext = context.applicationContext
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Log.e("SpeechRecognizer", "Speech recognition is not available on this device.")
            return
        }

        // Try on-device speech recognizer if available on Android 12+ (minSdk 31), fallback to standard recognizer
        speechRecognizer = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } catch (e: Exception) {
                Log.w("SpeechRecognizer", "Failed to create on-device recognizer, falling back: ${e.message}")
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _partialText.value = ""
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Smooth normalized sound volume level for visualizer
                val targetLevel = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
                _audioLevel.value = _audioLevel.value * 0.3f + targetLevel * 0.7f
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _audioLevel.value = 0f
            }

            override fun onError(error: Int) {
                _isListening.value = false
                _audioLevel.value = 0f
                val errorMessage = getErrorMessage(error)
                Log.e("SpeechRecognizer", "Error code $error: $errorMessage")

                // Recover from client / busy errors by resetting speech recognizer
                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    initRecognizer()
                }

                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onErrorListener?.invoke(errorMessage)
                } else {
                    onErrorListener?.invoke("No se escuchó nada en español. Intenta hablar de nuevo.")
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                _audioLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val bestCandidate = SpanishLanguageUtils.selectBestSpanishCandidate(matches)
                    val formattedText = SpanishLanguageUtils.formatSpanishText(bestCandidate)
                    _partialText.value = formattedText
                    onResultListener?.invoke(formattedText)
                } else {
                    onErrorListener?.invoke("No se encontraron resultados de voz en español")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val bestCandidate = SpanishLanguageUtils.selectBestSpanishCandidate(matches)
                    val formattedText = SpanishLanguageUtils.formatSpanishText(bestCandidate)
                    _partialText.value = formattedText
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        overrideLocaleTag: String? = null
    ) {
        if (speechRecognizer == null) {
            initRecognizer()
        }

        if (speechRecognizer == null) {
            onError("Speech recognition not supported on this device")
            return
        }

        this.onResultListener = onResult
        this.onErrorListener = onError

        val languageTag = when {
            !overrideLocaleTag.isNullOrBlank() -> overrideLocaleTag
            preferredSpanishLocaleTag.isNotBlank() -> preferredSpanishLocaleTag
            Locale.getDefault().language == "es" -> Locale.getDefault().toString()
            else -> "es-ES"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            // Force strict preference for requested Spanish language tag
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

            // Extended silence timeouts tailored for Spanish cadence and natural pauses
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _isListening.value = false
            Log.e("SpeechRecognizer", "Error starting listener: ${e.message}")
            // Re-initialize and retry once if canceled/invalid state
            initRecognizer()
            try {
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e2: Exception) {
                onError("No se pudo activar el micrófono: ${e2.message}")
            }
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w("SpeechRecognizer", "Error stopping listener: ${e.message}")
        }
        _isListening.value = false
        _audioLevel.value = 0f
    }

    fun destroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechRecognizer", "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null
        _isListening.value = false
        _audioLevel.value = 0f
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Error al grabar audio con el micrófono"
            SpeechRecognizer.ERROR_CLIENT -> "Error de cliente en el reconocedor de voz"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono no concedido"
            SpeechRecognizer.ERROR_NETWORK -> "Error de red al procesar voz"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red agotado"
            SpeechRecognizer.ERROR_NO_MATCH -> "No se detectó voz en español. Intenta de nuevo."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El servicio de voz está ocupado"
            SpeechRecognizer.ERROR_SERVER -> "Error en el servidor de reconocimiento de voz"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se escuchó voz. Por favor habla de nuevo."
            else -> "Error de reconocimiento de voz ($errorCode)"
        }
    }
}

