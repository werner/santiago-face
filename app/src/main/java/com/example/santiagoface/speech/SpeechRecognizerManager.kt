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
    private val audioFeatureExtractor = AudioFeatureExtractor()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var onResultWithAudioListener: ((String, AudioAnalysisResult?) -> Unit)? = null
    private var onResultListener: ((String) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    private var preferredSpanishLocaleTag: String = "es-ES"
    private var needsReinitialization: Boolean = false
    private var lastOverrideLocaleTag: String? = null
    private var isUsingOnDeviceRecognizer: Boolean = false
    private val networkFallbackPolicy = NetworkFallbackPolicy()

    init {
        initRecognizer()
    }

    fun updateSpanishLocale(localeTag: String) {
        preferredSpanishLocaleTag = if (localeTag.isNotBlank()) localeTag else "es-ES"
    }

    private fun initRecognizer(useOnDevice: Boolean = networkFallbackPolicy.useOfflineFallback) {
        destroyRecognizerQuietly()

        val appContext = context.applicationContext
        try {
            if (useOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                isUsingOnDeviceRecognizer = true
                Log.i("SpeechRecognizer", "Initialized on-device speech recognizer.")
            } else {
                if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                    Log.e("SpeechRecognizer", "Speech recognition is not available on this device.")
                    return
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                isUsingOnDeviceRecognizer = false
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Failed to create speech recognizer: ${e.message}")
            speechRecognizer = null
            isUsingOnDeviceRecognizer = false
            return
        }

        needsReinitialization = false

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _partialText.value = ""
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                val targetLevel = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
                _audioLevel.value = _audioLevel.value * 0.3f + targetLevel * 0.7f
                audioFeatureExtractor.addRmsDb(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _audioLevel.value = 0f
            }

            override fun onError(error: Int) {
                _isListening.value = false
                _audioLevel.value = 0f
                audioFeatureExtractor.stop()
                val errorMessage = getErrorMessage(error)
                Log.e("SpeechRecognizer", "Error code $error: $errorMessage")

                val isNetworkError =
                    error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT

                when (networkFallbackPolicy.onError(isNetworkError)) {
                    NetworkFallbackPolicy.FailureAction.RETRY_ONLINE -> {
                        Log.w("SpeechRecognizer", "Network error ($error); retrying online recognition.")
                        startListeningInternal()
                        return
                    }
                    NetworkFallbackPolicy.FailureAction.SWITCH_TO_OFFLINE -> {
                        Log.w("SpeechRecognizer", "Network error ($error) persisted; switching to on-device recognition.")
                        initRecognizer(useOnDevice = true)
                        if (speechRecognizer != null) {
                            startListeningInternal()
                            return
                        }
                    }
                    NetworkFallbackPolicy.FailureAction.REPORT_ERROR -> Unit
                }

                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    needsReinitialization = true
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
                networkFallbackPolicy.onSuccess()
                val audioAnalysis = audioFeatureExtractor.stopAndAnalyze()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val bestCandidate = SpanishLanguageUtils.selectBestSpanishCandidate(matches)
                    val formattedText = SpanishLanguageUtils.formatSpanishText(bestCandidate)
                    _partialText.value = formattedText
                    if (onResultWithAudioListener != null) {
                        onResultWithAudioListener?.invoke(formattedText, audioAnalysis)
                    } else {
                        onResultListener?.invoke(formattedText)
                    }
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
        onResult: (text: String, audioAnalysis: AudioAnalysisResult?) -> Unit,
        onError: (String) -> Unit,
        overrideLocaleTag: String? = null
    ) {
        this.onResultWithAudioListener = onResult
        this.onResultListener = null
        this.onErrorListener = onError
        this.lastOverrideLocaleTag = overrideLocaleTag

        networkFallbackPolicy.refreshForNewOperation()
        startListeningInternal()
    }

    private fun startListeningInternal() {
        val shouldUseOnDevice = networkFallbackPolicy.useOfflineFallback
        if (speechRecognizer == null || needsReinitialization || isUsingOnDeviceRecognizer != shouldUseOnDevice) {
            initRecognizer(useOnDevice = shouldUseOnDevice)
        }

        if (speechRecognizer == null) {
            onErrorListener?.invoke("Speech recognition not supported on this device")
            return
        }

        val languageTag = when {
            !lastOverrideLocaleTag.isNullOrBlank() -> lastOverrideLocaleTag!!
            preferredSpanishLocaleTag.isNotBlank() -> preferredSpanishLocaleTag
            Locale.getDefault().language == "es" -> Locale.getDefault().toString()
            else -> "es-ES"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            if (shouldUseOnDevice) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        try {
            _partialText.value = ""
            audioFeatureExtractor.start()
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _isListening.value = false
            audioFeatureExtractor.stop()
            Log.e("SpeechRecognizer", "Error starting listener: ${e.message}")
            initRecognizer(useOnDevice = shouldUseOnDevice)
            try {
                audioFeatureExtractor.start()
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e2: Exception) {
                audioFeatureExtractor.stop()
                onErrorListener?.invoke("No se pudo activar el micrófono: ${e2.message}")
            }
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        overrideLocaleTag: String?
    ) {
        startListening(
            onResult = { text, _ -> onResult(text) },
            onError = onError,
            overrideLocaleTag = overrideLocaleTag
        )
    }

    fun stopListening() {
        audioFeatureExtractor.stop()
        networkFallbackPolicy.reset()
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w("SpeechRecognizer", "Error stopping listener: ${e.message}")
        }
        _isListening.value = false
        _audioLevel.value = 0f
    }

    private fun destroyRecognizerQuietly() {
        audioFeatureExtractor.stop()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechRecognizer", "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    fun destroy() {
        destroyRecognizerQuietly()
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
