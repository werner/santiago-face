package com.example.santiagoface.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.santiagoface.camera.CameraHelper
import com.example.santiagoface.data.AgentRepository
import com.example.santiagoface.data.VisionAnalysisDto
import com.example.santiagoface.data.VisualMatchDto
import com.example.santiagoface.speech.SpeechRecognizerManager
import com.example.santiagoface.speech.TtsManager
import com.example.santiagoface.ui.face.RobotEmotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val cameraHelper = CameraHelper(application)

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

    private val _agentName = MutableStateFlow("SANTIAGO")
    val agentName: StateFlow<String> = _agentName.asStateFlow()

    // State Variables
    private val _emotion = MutableStateFlow(RobotEmotion.NEUTRAL)
    val emotion: StateFlow<RobotEmotion> = _emotion.asStateFlow()

    private val _lastUserText = MutableStateFlow("")
    val lastUserText: StateFlow<String> = _lastUserText.asStateFlow()

    private val _lastAgentText = MutableStateFlow("")
    val lastAgentText: StateFlow<String> = _lastAgentText.asStateFlow()

    private val _isMicEnabled = MutableStateFlow(false)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

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

    // Camera & Vision State
    val isCameraActive: StateFlow<Boolean> = cameraHelper.isCameraActive
    val cameraFovDeg: StateFlow<Float> = cameraHelper.cameraFovDeg
    val isFrontCamera: StateFlow<Boolean> = cameraHelper.isFrontCamera

    private val _lastVisionAnalysis = MutableStateFlow<VisionAnalysisDto?>(null)
    val lastVisionAnalysis: StateFlow<VisionAnalysisDto?> = _lastVisionAnalysis.asStateFlow()

    private val _lastVisualMatches = MutableStateFlow<List<VisualMatchDto>?>(null)
    val lastVisualMatches: StateFlow<List<VisualMatchDto>?> = _lastVisualMatches.asStateFlow()

    private val _isContinuousVisionLoopActive = MutableStateFlow(false)
    val isContinuousVisionLoopActive: StateFlow<Boolean> = _isContinuousVisionLoopActive.asStateFlow()

    private val _activeGoal = MutableStateFlow<String?>(null)
    val activeGoal: StateFlow<String?> = _activeGoal.asStateFlow()

    private val _activeTarget = MutableStateFlow<String?>(null)
    val activeTarget: StateFlow<String?> = _activeTarget.asStateFlow()

    private var visionLoopJob: Job? = null

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

    fun startCamera(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider? = null) {
        cameraHelper.startCamera(lifecycleOwner, previewSurfaceProvider, isFront = _isFrontCameraState)
    }

    private var _isFrontCameraState = true

    fun toggleCameraLens(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider? = null) {
        _isFrontCameraState = !_isFrontCameraState
        cameraHelper.setFrontCamera(_isFrontCameraState)
        if (isCameraActive.value) {
            cameraHelper.startCamera(lifecycleOwner, previewSurfaceProvider, isFront = _isFrontCameraState)
        }
    }

    fun stopCamera() {
        stopContinuousVisionLoop()
        cameraHelper.stopCamera()
    }

    fun toggleCamera(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider? = null) {
        if (isCameraActive.value) {
            stopCamera()
        } else {
            startCamera(lifecycleOwner, previewSurfaceProvider)
        }
    }

    fun enableMicrophone() {
        _isMicEnabled.value = true
        startListening()
    }

    fun disableMicrophone() {
        _isMicEnabled.value = false
        speechRecognizer.stopListening()
    }

    fun toggleListening() {
        if (_isMicEnabled.value) {
            disableMicrophone()
        } else {
            enableMicrophone()
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
                if (_isMicEnabled.value && !_isThinking.value && !ttsManager.isSpeaking.value) {
                    viewModelScope.launch {
                        delay(1000)
                        if (_isMicEnabled.value && !isListening.value && !_isThinking.value && !ttsManager.isSpeaking.value) {
                            startListening()
                        }
                    }
                }
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
        sounds: String? = null,
        overrideGoal: String? = null,
        overrideTarget: String? = null,
        referenceSizeCm: Float? = null,
        targetDistanceCm: Float? = null,
        autoAct: Boolean? = true
    ) {
        val trimmed = message.trim()
        if (trimmed.isEmpty() && audioInformation.isNullOrBlank() && voiceFeatures.isNullOrBlank() && sounds.isNullOrBlank() && !isCameraActive.value) return

        _lastUserText.value = trimmed.ifEmpty { "[Input con Cámara/Audio]" }
        _isThinking.value = true

        viewModelScope.launch {
            // Check if camera is active to attach current frames and FOV
            var imagePayload: String? = null
            var imagesPayload: List<String>? = null
            var fovPayload: Float? = null

            if (cameraHelper.isCameraActive.value) {
                fovPayload = cameraHelper.cameraFovDeg.value
                // Capture 2 sequential frames ~300ms apart for closing_rate computation
                val frames = cameraHelper.getSequentialFramesBase64(300L, 640, 480, 80)
                if (frames.size >= 2) {
                    imagesPayload = frames
                } else if (frames.isNotEmpty()) {
                    imagePayload = frames[0]
                }
            }

            val goal = overrideGoal ?: _activeGoal.value
            val target = overrideTarget ?: _activeTarget.value

            val result = agentRepository.sendMessage(
                baseUrl = _agentBaseUrl.value,
                userMessage = trimmed,
                audioInformation = audioInformation,
                voiceFeatures = voiceFeatures,
                sounds = sounds,
                image = imagePayload,
                images = imagesPayload,
                goal = goal,
                target = target,
                referenceSizeCm = referenceSizeCm,
                targetDistanceCm = targetDistanceCm,
                cameraFovDeg = fovPayload,
                autoAct = autoAct
            )
            _isThinking.value = false

            result.onSuccess { response ->
                val parsedEmotion = RobotEmotion.fromString(response.emotion)
                _emotion.value = parsedEmotion

                _lastVisionAnalysis.value = response.visionAnalysis
                _lastVisualMatches.value = response.visualMatches

                val replyText = response.text?.trim() ?: "No text reply"
                _lastAgentText.value = replyText

                // Check if response triggered or updated continuous visual loop
                val analysis = response.visionAnalysis
                if (analysis != null && cameraHelper.isCameraActive.value) {
                    val navBehavior = analysis.navigationBehavior.lowercase()
                    if (navBehavior == "approach" || navBehavior == "search" || navBehavior == "align") {
                        val targetQuery = analysis.targetQuery ?: target
                        startContinuousVisionLoop(
                            goal = if (navBehavior == "search") "search" else "approach",
                            target = targetQuery,
                            cameraFovDeg = fovPayload
                        )
                    }
                }

                speakReply(replyText)
            }.onFailure { error ->
                _emotion.value = RobotEmotion.SAD
                val errorMsg = "Error: ${error.message ?: "Failed to connect to agent"}"
                _lastAgentText.value = errorMsg
                if (_isMicEnabled.value) {
                    viewModelScope.launch {
                        delay(1500)
                        if (_isMicEnabled.value && !isListening.value && !_isThinking.value && !ttsManager.isSpeaking.value) {
                            startListening()
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts the fast autonomous visual control loop (calling POST /api/vision/act every loopIntervalMs).
     */
    fun startContinuousVisionLoop(
        goal: String = "approach",
        target: String? = null,
        category: String? = null,
        scanDirection: String? = null,
        referenceSizeCm: Float? = null,
        targetDistanceCm: Float? = null,
        cameraFovDeg: Float? = null,
        loopIntervalMs: Long = 1000L
    ) {
        stopContinuousVisionLoop()
        _isContinuousVisionLoopActive.value = true
        _activeGoal.value = goal
        _activeTarget.value = target

        visionLoopJob = viewModelScope.launch {
            while (_isContinuousVisionLoopActive.value) {
                val frame = cameraHelper.getSingleFrameBase64(640, 480, 80)
                val fov = cameraFovDeg ?: cameraHelper.cameraFovDeg.value

                val actResult = agentRepository.sendVisionAct(
                    baseUrl = _agentBaseUrl.value,
                    image = frame,
                    goal = goal,
                    target = target,
                    category = category,
                    scanDirection = scanDirection,
                    referenceSizeCm = referenceSizeCm,
                    targetDistanceCm = targetDistanceCm,
                    cameraFovDeg = fov,
                    executeHardware = true
                )

                actResult.onSuccess { response ->
                    _lastVisionAnalysis.value = response.visionAnalysis
                    _lastVisualMatches.value = response.visualMatches

                    if (response.navigationBehavior.equals("stop_at_target", ignoreCase = true)) {
                        stopContinuousVisionLoop()
                        _emotion.value = RobotEmotion.HAPPY
                        val text = response.actionReason.ifBlank { "¡Objetivo alcanzado!" }
                        _lastAgentText.value = text
                        speakReply(text)
                    }
                }.onFailure { err ->
                    // Continue loop next tick
                }

                delay(loopIntervalMs)
            }
        }
    }

    fun stopContinuousVisionLoop() {
        _isContinuousVisionLoopActive.value = false
        visionLoopJob?.cancel()
        visionLoopJob = null
        _activeGoal.value = null
        _activeTarget.value = null
    }

    private fun speakReply(text: String) {
        ttsManager.speak(
            text = text,
            onStart = {},
            onDone = {
                if (_isMicEnabled.value || _autoListen.value) {
                    viewModelScope.launch {
                        delay(600)
                        if (_isMicEnabled.value || _autoListen.value) {
                            startListening()
                        }
                    }
                }
            },
            onError = {
                if (_isMicEnabled.value || _autoListen.value) {
                    viewModelScope.launch {
                        delay(600)
                        if (_isMicEnabled.value || _autoListen.value) {
                            startListening()
                        }
                    }
                }
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
            }
        }

        viewModelScope.launch {
            val nameResult = agentRepository.fetchAgentName(_agentBaseUrl.value)
            nameResult.onSuccess { name ->
                if (name.isNotBlank()) {
                    _agentName.value = name.uppercase()
                }
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
        stopCamera()
        speechRecognizer.destroy()
        ttsManager.shutdown()
    }
}
