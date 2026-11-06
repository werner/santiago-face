package com.example.santiagoface.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.santiagoface.data.AgentRepository
import com.example.santiagoface.speech.SpeechRecognizerManager
import com.example.santiagoface.speech.TtsManager
import com.example.santiagoface.ui.face.RobotEmotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RobotViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("santiago_prefs", Context.MODE_PRIVATE)

    private val defaultUrl = com.example.santiagoface.BuildConfig.DEFAULT_URL
    private val apiKey = com.example.santiagoface.BuildConfig.API_KEY

    private val agentRepository = AgentRepository(apiKey = apiKey)
    val speechRecognizer = SpeechRecognizerManager(application)
    val ttsManager = TtsManager(application)

    private val savedUrl = prefs.getString("agent_url", null)
    private val initialUrl: String = run {
        if (savedUrl.isNullOrBlank() || savedUrl.contains("127.0.0.1") || savedUrl.contains("10.0.2.2") || savedUrl.contains("localhost")) {
            defaultUrl
        } else {
            agentRepository.cleanBaseUrl(savedUrl)
        }
    }

    private val _agentBaseUrl = MutableStateFlow(initialUrl)
    val agentBaseUrl: StateFlow<String> = _agentBaseUrl.asStateFlow()

    // State Variables
    private val _emotion = MutableStateFlow(RobotEmotion.NEUTRAL)
    val emotion: StateFlow<RobotEmotion> = _emotion.asStateFlow()

    private val _lastUserText = MutableStateFlow("")
    val lastUserText: StateFlow<String> = _lastUserText.asStateFlow()

    private val _lastAgentText = MutableStateFlow("")
    val lastAgentText: StateFlow<String> = _lastAgentText.asStateFlow()

    val isListening: StateFlow<Boolean> = speechRecognizer.isListening

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val isSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking
    val spokenWord: StateFlow<String> = ttsManager.currentSpokenWord
    val mouthOpening: StateFlow<Float> = ttsManager.mouthOpening

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _showTextInput = MutableStateFlow(false)
    val showTextInput: StateFlow<Boolean> = _showTextInput.asStateFlow()

    private val _autoListen = MutableStateFlow(prefs.getBoolean("auto_listen", false))
    val autoListen: StateFlow<Boolean> = _autoListen.asStateFlow()

    private val _spanishLocale = MutableStateFlow(prefs.getString("spanish_locale", "es-ES") ?: "es-ES")
    val spanishLocale: StateFlow<String> = _spanishLocale.asStateFlow()

    private val _connectionStatus = MutableStateFlow<Boolean?>(null)
    val connectionStatus: StateFlow<Boolean?> = _connectionStatus.asStateFlow()

    val audioLevel: StateFlow<Float> = speechRecognizer.audioLevel
    val partialSpeechText: StateFlow<String> = speechRecognizer.partialText

    private val _currentLatitude = MutableStateFlow<Double?>(null)
    val currentLatitude: StateFlow<Double?> = _currentLatitude.asStateFlow()

    private val _currentLongitude = MutableStateFlow<Double?>(null)
    val currentLongitude: StateFlow<Double?> = _currentLongitude.asStateFlow()

    init {
        // Configure initial Spanish locale tag for speech recognizer
        speechRecognizer.updateSpanishLocale(_spanishLocale.value)
        // Persist the clean initial URL
        prefs.edit().putString("agent_url", initialUrl).apply()
        // Initial health check
        testConnection()
    }

    fun updateSpanishLocale(localeTag: String) {
        _spanishLocale.value = localeTag
        prefs.edit().putString("spanish_locale", localeTag).apply()
        speechRecognizer.updateSpanishLocale(localeTag)
    }

    fun updateAgentUrl(newUrl: String) {
        val cleanUrl = agentRepository.cleanBaseUrl(newUrl)
        _agentBaseUrl.value = cleanUrl
        prefs.edit().putString("agent_url", cleanUrl).apply()
        testConnection()
    }

    fun toggleAutoListen(enabled: Boolean) {
        _autoListen.value = enabled
        prefs.edit().putBoolean("auto_listen", enabled).apply()
    }

    fun setEmotion(newEmotion: RobotEmotion) {
        _emotion.value = newEmotion
    }

    fun setShowSettings(show: Boolean) {
        _showSettings.value = show
    }

    fun setShowTextInput(show: Boolean) {
        _showTextInput.value = show
    }

    fun toggleListening() {
        if (ttsManager.isSpeaking.value) {
            ttsManager.stop()
            return
        }

        if (isListening.value) {
            speechRecognizer.stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (_isThinking.value) return

        if (ttsManager.isSpeaking.value) {
            ttsManager.stop()
        }

        speechRecognizer.startListening(
            onResult = { recognizedText, audioAnalysis ->
                _lastUserText.value = recognizedText
                processUserMessage(
                    message = recognizedText,
                    audioInformation = audioAnalysis?.audioInformation,
                    voiceFeatures = audioAnalysis?.voiceFeatures,
                    sounds = audioAnalysis?.sounds
                )
            },
            onError = { errorMessage ->
                // Logged by SpeechRecognizerManager
            },
            overrideLocaleTag = _spanishLocale.value
        )
    }

    fun updateLocation(lat: Double, lon: Double) {
        _currentLatitude.value = lat
        _currentLongitude.value = lon
        viewModelScope.launch {
            agentRepository.sendLocation(_agentBaseUrl.value, lat, lon)
        }
    }

    fun processUserMessage(
        message: String,
        audioInformation: String? = null,
        voiceFeatures: String? = null,
        sounds: String? = null
    ) {
        val trimmed = message.trim()
        if (trimmed.isEmpty() && audioInformation.isNullOrBlank() && voiceFeatures.isNullOrBlank() && sounds.isNullOrBlank()) return

        _lastUserText.value = trimmed.ifEmpty { "[Audio/Sound Input]" }
        _isThinking.value = true

        viewModelScope.launch {
            val result = agentRepository.sendMessage(
                baseUrl = _agentBaseUrl.value,
                userMessage = trimmed,
                audioInformation = audioInformation,
                voiceFeatures = voiceFeatures,
                sounds = sounds
            )
            _isThinking.value = false

            result.onSuccess { response ->
                // Update Emotion from JSON ("happy" | "sad" | "angry" | "curious" | "neutral")
                val parsedEmotion = RobotEmotion.fromString(response.emotion)
                _emotion.value = parsedEmotion

                val replyText = response.text?.trim() ?: "No text reply"
                _lastAgentText.value = replyText

                // Speak the reply via TTS (Tool calls are ignored per requirement)
                speakReply(replyText)
            }.onFailure { error ->
                _emotion.value = RobotEmotion.SAD
                val errorMsg = "Error: ${error.message ?: "Failed to connect to agent"}"
                _lastAgentText.value = errorMsg
            }
        }
    }

    private fun speakReply(text: String) {
        ttsManager.speak(
            text = text,
            onStart = {},
            onDone = {
                if (_autoListen.value) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(600)
                        startListening()
                    }
                }
            },
            onError = {}
        )
    }

    fun testConnection() {
        viewModelScope.launch {
            _connectionStatus.value = null
            val healthResult = agentRepository.checkHealth(_agentBaseUrl.value)
            healthResult.onSuccess { isOk ->
                _connectionStatus.value = isOk
            }.onFailure { err ->
                _connectionStatus.value = false
            }
        }
        
        viewModelScope.launch {
            val lat = _currentLatitude.value
            val lon = _currentLongitude.value
            if (lat != null && lon != null) {
                agentRepository.sendLocation(_agentBaseUrl.value, lat, lon)
            }
        }
    }

    fun resetHistory() {
        viewModelScope.launch {
            _isThinking.value = true
            val resetResult = agentRepository.resetHistory(_agentBaseUrl.value)
            _isThinking.value = false

            if (resetResult.getOrDefault(false)) {
                _emotion.value = RobotEmotion.HAPPY
                _lastAgentText.value = "Memory reset"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
        ttsManager.shutdown()
    }
}
