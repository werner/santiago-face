package com.example.santiagoface

import com.example.santiagoface.camera.DetectedObject
import com.example.santiagoface.data.BoundingBoxDto
import com.example.santiagoface.data.Esp32MoveResult
import com.example.santiagoface.data.Esp32StatusResult
import com.example.santiagoface.navigation.ObjectSearchController
import com.example.santiagoface.navigation.SearchMotorTransport
import com.example.santiagoface.navigation.SearchState
import com.example.santiagoface.ui.face.RobotEmotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMotorTransport(
    private val moveHandler: (action: String, speed: Int?) -> Esp32MoveResult = { action, _ ->
        Esp32MoveResult(status = "ok", action = action, distanceCm = 80f)
    },
    private val statusHandler: () -> Esp32StatusResult? = {
        Esp32StatusResult(obstacleBlocked = false, distanceCm = 80f, state = "stop")
    }
) : SearchMotorTransport {
    val recordedActions = mutableListOf<String>()

    override suspend fun move(action: String, speed: Int?, durationMs: Long?): Esp32MoveResult {
        recordedActions.add(action)
        return moveHandler(action, speed)
    }

    override suspend fun getStatus(): Esp32StatusResult? = statusHandler()
}

class ObjectSearchControllerTest {

    @Test
    fun `scanning centers approaches and finds target by areaRatio`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var detections: List<DetectedObject> = emptyList()
        var spoken = ""
        var emotion = RobotEmotion.NEUTRAL
        var ended = false

        val transport = FakeMotorTransport(moveHandler = { action, _ ->
            // After first scan pulse, cup appears on the right
            if (action == "right" && detections.isEmpty()) {
                detections = listOf(
                    DetectedObject(
                        label = "Cup",
                        confidence = 0.85f,
                        boundingBox = BoundingBoxDto(centerX = 0.8f, areaRatio = 0.08f),
                        centerXNorm = 0.6f,
                        areaRatio = 0.08f
                    )
                )
            } else if (action == "right" && detections.firstOrNull()?.centerXNorm == 0.6f) {
                // After centering right, cup is centered and small
                detections = listOf(
                    DetectedObject(
                        label = "Cup",
                        confidence = 0.88f,
                        boundingBox = BoundingBoxDto(centerX = 0.5f, areaRatio = 0.10f),
                        centerXNorm = 0.0f,
                        areaRatio = 0.10f
                    )
                )
            } else if (action == "forward") {
                // Approaching brings cup to areaRatio >= 0.22
                detections = listOf(
                    DetectedObject(
                        label = "Cup",
                        confidence = 0.92f,
                        boundingBox = BoundingBoxDto(centerX = 0.5f, areaRatio = 0.25f),
                        centerXNorm = 0.0f,
                        areaRatio = 0.25f
                    )
                )
            }
            Esp32MoveResult(status = "ok", action = action, distanceCm = 45f)
        })

        val controller = ObjectSearchController(
            scope = scope,
            transport = transport,
            detectionsProvider = { detections },
            onEmotionChange = { emotion = it },
            onSpeak = { spoken = it },
            onSearchEnded = { ended = true },
            loopIntervalMs = 20L
        )

        controller.startSearch("cup")
        val deadline = System.currentTimeMillis() + 5000L
        while (!ended && System.currentTimeMillis() < deadline) {
            delay(25L)
        }

        assertTrue("Search should have completed", ended)
        assertEquals(SearchState.IDLE, controller.searchState.value)
        assertEquals(RobotEmotion.HAPPY, emotion)
        assertTrue(spoken.contains("cup"))
        assertTrue(transport.recordedActions.contains("forward"))
        assertEquals("stop", transport.recordedActions.last())
        scope.cancel()
    }

    @Test
    fun `obstacle ahead triggers avoidance and flips from stuck right to left`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var detections = listOf(
            DetectedObject(
                label = "Bottle",
                confidence = 0.80f,
                centerXNorm = 0.0f,
                areaRatio = 0.05f
            )
        )
        var blockNextForward = true

        val transport = FakeMotorTransport(moveHandler = { action, _ ->
            when {
                action == "forward" && blockNextForward -> {
                    blockNextForward = false
                    // Obstacle detected at 30 cm (outside 25 cm target-reached zone)
                    detections = emptyList()
                    Esp32MoveResult(status = "blocked", reason = "obstacle_ahead", distanceCm = 30.0f)
                }
                action == "right" -> {
                    // Simulate right turn stuck/blocked so controller must fall back to left
                    Esp32MoveResult(status = "blocked", reason = "obstacle_ahead", distanceCm = 30.0f)
                }
                else -> Esp32MoveResult(status = "ok", action = action, distanceCm = 60f)
            }
        })

        val controller = ObjectSearchController(
            scope = scope,
            transport = transport,
            detectionsProvider = { detections },
            loopIntervalMs = 20L
        )

        controller.startSearch("bottle")
        val deadline = System.currentTimeMillis() + 5000L
        while (!transport.recordedActions.contains("left") && System.currentTimeMillis() < deadline) {
            delay(25L)
        }

        assertTrue("Should back up during avoidance", transport.recordedActions.contains("backward"))
        assertTrue("Should fall back to left when right is blocked", transport.recordedActions.contains("left"))
        controller.cancel()
        assertEquals(SearchState.IDLE, controller.searchState.value)
        scope.cancel()
    }

    @Test
    fun `immediate cancel stops motors and resets to IDLE`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transport = FakeMotorTransport()
        var ended = false

        val controller = ObjectSearchController(
            scope = scope,
            transport = transport,
            detectionsProvider = { emptyList() },
            onSearchEnded = { ended = true },
            loopIntervalMs = 50L
        )

        controller.startSearch("cell phone")
        delay(80L)
        assertEquals(SearchState.SCANNING, controller.searchState.value)

        controller.cancel(speakFeedback = true)
        delay(50L)

        assertEquals(SearchState.IDLE, controller.searchState.value)
        assertTrue(ended)
        assertTrue(transport.recordedActions.contains("stop"))
        scope.cancel()
    }
}
