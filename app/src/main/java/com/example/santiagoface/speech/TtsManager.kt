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

    private var onStartCallback: (() -> Unit)? = null
    private var onDoneCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    init {
        setupUtteranceListener()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Language US is not supported or missing data")
            }
            // Give a cool slightly robotic pitch & rate
            tts?.setPitch(1.05f)
            tts?.setSpeechRate(1.0f)
            isInitialized = true
        } else {
            Log.e("TtsManager", "TTS Initialization failed with status: $status")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                onStartCallback?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onDoneCallback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onErrorCallback?.invoke("TTS Error for utterance $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                onErrorCallback?.invoke("TTS Error code $errorCode")
            }
        })
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

        this.onStartCallback = onStart
        this.onDoneCallback = onDone
        this.onErrorCallback = onError

        val utteranceId = "Santiago_Utterance_${System.currentTimeMillis()}"
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId

        @Suppress("DEPRECATION")
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)

        if (result == TextToSpeech.ERROR) {
            _isSpeaking.value = false
            onError?.invoke("Failed to enqueue TTS speech")
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
        _isSpeaking.value = false
    }

    fun shutdown() {
        if (isInitialized) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
        _isSpeaking.value = false
    }
}
