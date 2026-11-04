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

    private val _statusText = MutableStateFlow("Tap microphone to start talking")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _lastUserText = MutableStateFlow("")
    val lastUserText: StateFlow<String> = _lastUserText.asStateFlow()

    private val _lastAgentText = MutableStateFlow("")
    val lastAgentText: StateFlow<String> = _lastAgentText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

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

    private val _connectionStatus = MutableStateFlow<Boolean?>(null)
    val connectionStatus: StateFlow<Boolean?> = _connectionStatus.asStateFlow()

    val audioLevel: StateFlow<Float> = speechRecognizer.audioLevel
    val partialSpeechText: StateFlow<String> = speechRecognizer.partialText

    init {
        // Persist the clean initial URL
        prefs.edit().putString("agent_url", initialUrl).apply()
        // Initial health check
        testConnection()
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
            _statusText.value = "Speech stopped"
            return
        }

        if (_isListening.value) {
            speechRecognizer.stopListening()
            _isListening.value = false
            _statusText.value = "Stopped listening"
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (_isThinking.value) return

        if (ttsManager.isSpeaking.value) {
            ttsManager.stop()
        }

        _isListening.value = true
        _statusText.value = "Listening..."

        speechRecognizer.startListening(
            onResult = { recognizedText ->
                _isListening.value = false
                _lastUserText.value = recognizedText
                processUserMessage(recognizedText)
            },
            onError = { errorMessage ->
                _isListening.value = false
                _statusText.value = errorMessage
            }
        )
    }

    fun processUserMessage(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return

        _lastUserText.value = trimmed
        _isThinking.value = true
        _statusText.value = "thinking..."

        viewModelScope.launch {
            val result = agentRepository.sendMessage(_agentBaseUrl.value, trimmed)
            _isThinking.value = false

            result.onSuccess { response ->
                // Update Emotion from JSON ("happy" | "sad" | "angry" | "curious" | "neutral")
                val parsedEmotion = RobotEmotion.fromString(response.emotion)
                _emotion.value = parsedEmotion

                val replyText = response.text?.trim() ?: "No text reply"
                _lastAgentText.value = replyText
                _statusText.value = "Santiago: $replyText"

                // Speak the reply via TTS (Tool calls are ignored per requirement)
                speakReply(replyText)
            }.onFailure { error ->
                _emotion.value = RobotEmotion.SAD
                val errorMsg = "Error: ${error.message ?: "Failed to connect to agent"}"
                _statusText.value = errorMsg
                _lastAgentText.value = errorMsg
            }
        }
    }

    private fun speakReply(text: String) {
        ttsManager.speak(
            text = text,
            onStart = {
                _statusText.value = "Speaking..."
            },
            onDone = {
                _statusText.value = "Finished speaking"
                if (_autoListen.value) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(600)
                        startListening()
                    }
                }
            },
            onError = { err ->
                _statusText.value = "TTS error: $err"
            }
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
                _statusText.value = "Connection error: ${err.message}"
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
                _statusText.value = "Memory reset successfully!"
                _lastAgentText.value = "Memory reset"
            } else {
                _statusText.value = "Failed to reset memory"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
        ttsManager.shutdown()
    }
}
