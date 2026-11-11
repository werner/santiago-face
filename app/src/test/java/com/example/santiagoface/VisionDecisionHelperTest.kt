package com.example.santiagoface

import com.example.santiagoface.camera.DetectedObject
import com.example.santiagoface.camera.VisionDecisionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionDecisionHelperTest {

    private val sampleBottleDetection = DetectedObject(
        label = "Bottle",
        confidence = 0.88f
    )

    private val samplePersonDetection = DetectedObject(
        label = "Person",
        confidence = 0.94f
    )

    private val sampleCupDetection = DetectedObject(
        label = "Coffee cup",
        confidence = 0.82f
    )

    @Test
    fun returnsFalse_whenCameraIsInactive() {
        val result = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "mira esto",
            isCameraActive = false,
            mlKitDetections = emptyList()
        )
        assertFalse(result)
    }

    @Test
    fun returnsFalse_forGeneralConversationWithoutVisualIntent_savingBandwidth() {
        val result1 = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "Hola, ¿cómo estás?",
            isCameraActive = true,
            mlKitDetections = listOf(samplePersonDetection)
        )
        assertFalse("General greeting should not send Base64 images", result1)

        val result2 = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "Cuéntame un chiste",
            isCameraActive = true,
            mlKitDetections = listOf(samplePersonDetection)
        )
        assertFalse("Joke request should not send Base64 images", result2)
    }

    @Test
    fun returnsTrue_whenServerExplicitlyRequestedImage() {
        val result = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "sí",
            isCameraActive = true,
            mlKitDetections = emptyList(),
            serverExplicitlyRequestedImage = true
        )
        assertTrue("Must send image if server requested it", result)
    }

    @Test
    fun returnsFalse_whenMlKitConfidentlyRecognizesTargetOnDevice() {
        // User asks if robot sees a cup / taza
        val resultSpanish = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "¿Ves una taza?",
            isCameraActive = true,
            mlKitDetections = listOf(sampleCupDetection)
        )
        assertFalse("ML Kit already recognized cup on-device; should not send Base64 image", resultSpanish)

        // User asks if robot sees a bottle
        val resultEnglish = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "Do you see a bottle?",
            isCameraActive = true,
            mlKitDetections = listOf(sampleBottleDetection)
        )
        assertFalse("ML Kit already recognized bottle on-device; should not send Base64 image", resultEnglish)
    }

    @Test
    fun returnsTrue_whenTargetIsNotRecognizedByMlKit_fallingBackToServer() {
        // User asks for a laptop, but ML Kit only sees a cup
        val result = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "¿Dónde está la laptop?",
            isCameraActive = true,
            mlKitDetections = listOf(sampleCupDetection)
        )
        assertTrue("Target not recognized locally, must send image to server for deep vision", result)
    }

    @Test
    fun returnsTrue_whenDeepReasoningIsRequired_regardlessOfMlKit() {
        // 1. Reading text
        val readingResult = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "Leé lo que dice este papel",
            isCameraActive = true,
            mlKitDetections = listOf(sampleBottleDetection)
        )
        assertTrue("Reading text requires multimodal LLM reasoning", readingResult)

        // 2. Reading color / brand
        val brandResult = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "¿De qué marca es esta botella?",
            isCameraActive = true,
            mlKitDetections = listOf(sampleBottleDetection)
        )
        assertTrue("Brand identification requires deep reasoning", brandResult)

        val colorResult = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "¿De qué color es mi remera?",
            isCameraActive = true,
            mlKitDetections = listOf(samplePersonDetection)
        )
        assertTrue("Color identification requires deep reasoning", colorResult)

        // 3. Facial identity
        val identityResult = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "¿Quién es esta persona?",
            isCameraActive = true,
            mlKitDetections = listOf(samplePersonDetection)
        )
        assertTrue("Facial identity recognition requires server reasoning", identityResult)

        // 4. Detailed explanation
        val explainResult = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "Explicá detalladamente lo que ves",
            isCameraActive = true,
            mlKitDetections = listOf(sampleBottleDetection)
        )
        assertTrue("Detailed scene explanation requires server reasoning", explainResult)

        // 5. Visual scene queries like "¿Qué estás viendo?"
        val seeingResult = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "¿Qué estás viendo?",
            isCameraActive = true,
            mlKitDetections = listOf(sampleCupDetection)
        )
        assertTrue("Scene query '¿Qué estás viendo?' requires server multimodal vision", seeingResult)

        val lookingResult = VisionDecisionHelper.shouldSendBase64Image(
            userMessage = "What are you seeing?",
            isCameraActive = true,
            mlKitDetections = emptyList()
        )
        assertTrue("English scene query requires server multimodal vision", lookingResult)
    }

    @Test
    fun isMatch_handlesSpanishEnglishSynonyms() {
        assertTrue(VisionDecisionHelper.isMatch("Coffee cup", "taza"))
        assertTrue(VisionDecisionHelper.isMatch("Bottle", "botella"))
        assertTrue(VisionDecisionHelper.isMatch("Person", "persona"))
        assertTrue(VisionDecisionHelper.isMatch("Cell phone", "celular"))
        assertTrue(VisionDecisionHelper.isMatch("Laptop", "computadora"))
    }

    @Test
    fun extractTargetCandidate_extractsTargetNouns() {
        assertEquals("taza", VisionDecisionHelper.extractTargetCandidate("¿Ves una taza?"))
        assertEquals("bottle", VisionDecisionHelper.extractTargetCandidate("Do you see the bottle?"))
        assertEquals("celular", VisionDecisionHelper.extractTargetCandidate("Dónde está el celular"))
    }
}
