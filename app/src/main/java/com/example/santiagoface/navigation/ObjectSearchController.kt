package com.example.santiagoface.navigation

import com.example.santiagoface.camera.DetectedObject
import com.example.santiagoface.camera.MlKitVisionHelper
import com.example.santiagoface.data.Esp32Client
import com.example.santiagoface.data.Esp32MoveResult
import com.example.santiagoface.data.Esp32StatusResult
import com.example.santiagoface.ui.face.RobotEmotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SearchState {
    IDLE,
    SCANNING,
    CENTERING,
    APPROACHING,
    AVOIDING_OBSTACLE,
    TARGET_FOUND,
    NOT_FOUND
}

interface SearchMotorTransport {
    suspend fun move(action: String, speed: Int? = null, durationMs: Long? = null): Esp32MoveResult
    suspend fun getStatus(): Esp32StatusResult?
}

class Esp32SearchTransport(
    private val esp32Client: Esp32Client,
    private val baseUrlProvider: () -> String
) : SearchMotorTransport {
    override suspend fun move(action: String, speed: Int?, durationMs: Long?): Esp32MoveResult {
        return esp32Client.move(baseUrlProvider(), action, speed, durationMs)
    }

    override suspend fun getStatus(): Esp32StatusResult? {
        return esp32Client.getStatus(baseUrlProvider())
    }
}

/**
 * Local 10 Hz autonomous Object Search, Visual Servoing, and Obstacle Avoidance controller.
 * Runs entirely between Android (ML Kit) and ESP32 without streaming raw video to the PC.
 */
class ObjectSearchController(
    private val scope: CoroutineScope,
    private val transport: SearchMotorTransport,
    private val detectionsProvider: () -> List<DetectedObject>,
    private val onHighFrequencyVision: (Boolean) -> Unit = {},
    private val onEmotionChange: (RobotEmotion) -> Unit = {},
    private val onSpeak: (String) -> Unit = {},
    private val onSearchEnded: () -> Unit = {},
    private val loopIntervalMs: Long = 100L
) {
    private val _searchState = MutableStateFlow(SearchState.IDLE)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _targetLabel = MutableStateFlow<String?>(null)
    val targetLabel: StateFlow<String?> = _targetLabel.asStateFlow()

    private val _lastDistanceCm = MutableStateFlow<Float?>(null)
    val lastDistanceCm: StateFlow<Float?> = _lastDistanceCm.asStateFlow()

    private var searchJob: Job? = null

    fun isSearching(): Boolean = _searchState.value != SearchState.IDLE

    fun startSearch(label: String) {
        val cleanLabel = label.trim().lowercase()
        if (cleanLabel.isEmpty()) return

        cancelInternal(sendStop = false, notifyEnd = false)

        _targetLabel.value = cleanLabel
        _searchState.value = SearchState.SCANNING
        onHighFrequencyVision(true)
        onEmotionChange(RobotEmotion.CURIOUS)

        searchJob = scope.launch {
            try {
                runSearchLoop(cleanLabel)
            } catch (_: CancellationException) {
                // Cancelled by voice/UI interrupt
            } finally {
                onHighFrequencyVision(false)
            }
        }
    }

    fun cancel(speakFeedback: Boolean = false) {
        if (_searchState.value == SearchState.IDLE && searchJob == null) return
        cancelInternal(sendStop = true, notifyEnd = true)
        onEmotionChange(RobotEmotion.NEUTRAL)
        if (speakFeedback) {
            onSpeak("Búsqueda detenida.")
        }
    }

    private fun cancelInternal(sendStop: Boolean, notifyEnd: Boolean) {
        searchJob?.cancel()
        searchJob = null
        _searchState.value = SearchState.IDLE
        _targetLabel.value = null
        onHighFrequencyVision(false)
        if (sendStop) {
            scope.launch {
                transport.move("stop", 0)
            }
        }
        if (notifyEnd) {
            onSearchEnded()
        }
    }

    private suspend fun runSearchLoop(target: String) {
        var scanPulses = 0
        var relocationDone = false
        var scanDirection = "right"
        var avoidDirection = "right"
        var lostTargetTicks = 0
        var lastCenterX = 0f
        var lastBlockedDist: Float? = null

        val maxScanPulsesPerSweep = 16 // ~360 degrees with 250ms pulse + 250ms settle
        val maxLostTicksBeforeRescan = 5 // 500ms at 10 Hz

        while (scope.isActive && _searchState.value != SearchState.IDLE) {
            val match = findMatchingDetection(target, minConfidence = 0.60f)

            when (_searchState.value) {
                SearchState.SCANNING -> {
                    if (match != null) {
                        transport.move("stop", 0)
                        lostTargetTicks = 0
                        lastCenterX = match.centerXNorm
                        if (isTargetReached(match, _lastDistanceCm.value)) {
                            handleTargetFound(target)
                            return
                        }
                        _searchState.value = if (abs(match.centerXNorm) <= CENTER_DEADZONE) {
                            SearchState.APPROACHING
                        } else {
                            SearchState.CENTERING
                        }
                        delay(loopIntervalMs)
                        continue
                    }

                    // Pulsed rotation so camera frames are sharp during the settle pause
                    val pulseRes = transport.move(scanDirection, speed = 150)
                    updateDistance(pulseRes.distanceCm)
                    if (pulseRes.isBlocked || pulseRes.isError) {
                        // If moving right didn't clear/move, flip direction and avoid obstacle
                        if (scanDirection == "right") {
                            scanDirection = "left"
                            avoidDirection = "left"
                        } else {
                            scanDirection = "right"
                            avoidDirection = "right"
                        }
                        _searchState.value = SearchState.AVOIDING_OBSTACLE
                        continue
                    }

                    delay(250L)
                    transport.move("stop", 0)
                    delay(250L)

                    scanPulses++
                    if (scanPulses >= maxScanPulsesPerSweep) {
                        if (!relocationDone) {
                            relocationDone = true
                            scanPulses = 0
                            // Try a short forward relocation if clear before second 360° sweep
                            val status = transport.getStatus()
                            updateDistance(status?.distanceCm)
                            val frontClear = status?.forwardBlocked != true &&
                                (status?.distanceCm == null || status.distanceCm > 35f)
                            if (frontClear) {
                                val fwdRes = transport.move("forward", speed = 160)
                                updateDistance(fwdRes.distanceCm)
                                if (fwdRes.isBlocked) {
                                    _searchState.value = SearchState.AVOIDING_OBSTACLE
                                    continue
                                }
                                delay(500L)
                                transport.move("stop", 0)
                                delay(150L)
                            } else {
                                handleNotFound(target)
                                return
                            }
                        } else {
                            handleNotFound(target)
                            return
                        }
                    }
                }

                SearchState.CENTERING -> {
                    if (match == null) {
                        lostTargetTicks++
                        if (lostTargetTicks >= maxLostTicksBeforeRescan) {
                            transport.move("stop", 0)
                            scanDirection = if (lastCenterX < 0f) "left" else "right"
                            _searchState.value = SearchState.SCANNING
                        }
                        delay(loopIntervalMs)
                        continue
                    }

                    lostTargetTicks = 0
                    lastCenterX = match.centerXNorm

                    if (isTargetReached(match, _lastDistanceCm.value)) {
                        handleTargetFound(target)
                        return
                    }

                    if (abs(match.centerXNorm) <= CENTER_DEADZONE) {
                        transport.move("stop", 0)
                        _searchState.value = SearchState.APPROACHING
                    } else {
                        val turnAction = if (match.centerXNorm < 0f) "left" else "right"
                        val turnSpeed = (130 + (abs(match.centerXNorm) * 45f).toInt()).coerceIn(130, 175)
                        val res = transport.move(turnAction, speed = turnSpeed)
                        updateDistance(res.distanceCm)
                        if (res.isBlocked) {
                            _searchState.value = SearchState.AVOIDING_OBSTACLE
                            continue
                        }
                    }
                    delay(loopIntervalMs)
                }

                SearchState.APPROACHING -> {
                    if (match == null) {
                        lostTargetTicks++
                        if (lostTargetTicks >= maxLostTicksBeforeRescan) {
                            transport.move("stop", 0)
                            scanDirection = if (lastCenterX < 0f) "left" else "right"
                            _searchState.value = SearchState.SCANNING
                        }
                        delay(loopIntervalMs)
                        continue
                    }

                    lostTargetTicks = 0
                    lastCenterX = match.centerXNorm

                    if (isTargetReached(match, _lastDistanceCm.value)) {
                        handleTargetFound(target)
                        return
                    }

                    val moveAction: String
                    val moveSpeed: Int
                    if (match.centerXNorm < -CENTER_DEADZONE) {
                        moveAction = "left"
                        moveSpeed = 145
                    } else if (match.centerXNorm > CENTER_DEADZONE) {
                        moveAction = "right"
                        moveSpeed = 145
                    } else {
                        moveAction = "forward"
                        moveSpeed = 165
                    }

                    val res = transport.move(moveAction, speed = moveSpeed)
                    updateDistance(res.distanceCm)

                    if (res.isBlocked) {
                        // If HC-SR04 stopped us within 25 cm while target is centered, we reached it!
                        if (abs(match.centerXNorm) <= 0.20f &&
                            res.distanceCm != null && res.distanceCm <= REACHED_DISTANCE_CM
                        ) {
                            handleTargetFound(target)
                            return
                        }
                        // Otherwise, an obstacle is blocking the path before reaching the target
                        if (lastBlockedDist != null && res.distanceCm != null &&
                            abs(lastBlockedDist - res.distanceCm) < 2.0f
                        ) {
                            avoidDirection = if (avoidDirection == "right") "left" else "right"
                        }
                        lastBlockedDist = res.distanceCm
                        _searchState.value = SearchState.AVOIDING_OBSTACLE
                        continue
                    }

                    delay(loopIntervalMs)
                }

                SearchState.AVOIDING_OBSTACLE -> {
                    // Deterministic reactive obstacle-avoidance maneuver:
                    // stop (150ms) -> backward (400ms, 160) -> turn (~500ms, 170) -> forward bypass (600ms, 170) -> SCANNING
                    transport.move("stop", 0)
                    delay(150L)

                    val backRes = transport.move("backward", speed = 160)
                    updateDistance(backRes.distanceCm)
                    if (!backRes.isBlocked && !backRes.isError) {
                        delay(400L)
                        transport.move("stop", 0)
                        delay(80L)
                    }

                    // Turn away; if preferred direction is blocked/stuck, immediately flip to opposite
                    var turnRes = transport.move(avoidDirection, speed = 170)
                    updateDistance(turnRes.distanceCm)
                    if (turnRes.isBlocked || turnRes.isError) {
                        avoidDirection = if (avoidDirection == "right") "left" else "right"
                        turnRes = transport.move(avoidDirection, speed = 170)
                        updateDistance(turnRes.distanceCm)
                    }
                    delay(500L)
                    transport.move("stop", 0)
                    delay(100L)

                    val bypassRes = transport.move("forward", speed = 170)
                    updateDistance(bypassRes.distanceCm)
                    if (bypassRes.isBlocked) {
                        // Still blocked ahead: pivot more in avoidDirection instead of getting stuck
                        avoidDirection = if (avoidDirection == "right") "left" else "right"
                        transport.move(avoidDirection, speed = 170)
                        delay(500L)
                        transport.move("stop", 0)
                    } else {
                        delay(600L)
                        transport.move("stop", 0)
                    }
                    delay(150L)

                    scanPulses = 0
                    _searchState.value = SearchState.SCANNING
                }

                SearchState.TARGET_FOUND,
                SearchState.NOT_FOUND,
                SearchState.IDLE -> return
            }
        }
    }

    private suspend fun handleTargetFound(target: String) {
        _searchState.value = SearchState.TARGET_FOUND
        transport.move("stop", 0)
        onEmotionChange(RobotEmotion.HAPPY)
        onSpeak("¡Encontré el $target!")
        _searchState.value = SearchState.IDLE
        _targetLabel.value = null
        onSearchEnded()
    }

    private suspend fun handleNotFound(target: String) {
        _searchState.value = SearchState.NOT_FOUND
        transport.move("stop", 0)
        onEmotionChange(RobotEmotion.SAD)
        onSpeak("No pude encontrar $target.")
        _searchState.value = SearchState.IDLE
        _targetLabel.value = null
        onSearchEnded()
    }

    private fun updateDistance(dist: Float?) {
        if (dist != null) {
            _lastDistanceCm.value = dist
        }
    }

    private fun isTargetReached(match: DetectedObject, distanceCm: Float?): Boolean {
        if (match.areaRatio >= REACHED_AREA_RATIO) return true
        if (abs(match.centerXNorm) <= 0.20f && distanceCm != null && distanceCm <= REACHED_DISTANCE_CM) {
            return true
        }
        return false
    }

    private fun findMatchingDetection(targetQuery: String, minConfidence: Float): DetectedObject? {
        val normalized = targetQuery.trim().lowercase()
        val aliases = LABEL_ALIASES[normalized] ?: listOf(normalized)
        return detectionsProvider()
            .filter { det ->
                det.confidence >= minConfidence && aliases.any { alias ->
                    val label = det.label.lowercase()
                    label == alias || label.contains(alias) || alias.contains(label)
                }
            }
            .maxByOrNull { it.confidence }
    }

    companion object {
        const val CENTER_DEADZONE = 0.15f
        const val REACHED_AREA_RATIO = 0.22f
        const val REACHED_DISTANCE_CM = 25.0f

        private val LABEL_ALIASES = mapOf(
            "cup" to listOf("cup", "mug", "coffee cup", "teacup", "tableware"),
            "bottle" to listOf("bottle", "water bottle", "drinkware"),
            "cell phone" to listOf("cell phone", "mobile phone", "telephone", "smartphone", "phone"),
            "person" to listOf("person", "human", "man", "woman", "face", "people"),
            "dining table" to listOf("dining table", "table", "desk"),
            "tv" to listOf("tv", "television", "monitor", "screen"),
            "laptop" to listOf("laptop", "computer", "notebook"),
            "potted plant" to listOf("potted plant", "plant", "houseplant"),
            "sports ball" to listOf("sports ball", "ball"),
            "couch" to listOf("couch", "sofa")
        )
    }
}
