package com.example.santiagoface

import com.example.santiagoface.data.AgentRepository
import com.example.santiagoface.data.AgentRequest
import com.example.santiagoface.data.BoundingBoxDto
import com.example.santiagoface.data.MlKitDetectionDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRepositoryTest {

    private val repository = AgentRepository()

    @Test
    fun cleanBaseUrl_preservesHostnameAndEnsuresHttpPrefix() {
        assertEquals("http://santiago_agent.local:3000", repository.cleanBaseUrl("http://santiago_agent.local:3000"))
        assertEquals("http://santiago_agent.local:3000", repository.cleanBaseUrl("santiago_agent.local:3000"))
        assertEquals("http://santiago_agent.local:3000", repository.cleanBaseUrl("santiago_agent.local:3000/"))
        assertEquals("https://santiago_agent.local:3000", repository.cleanBaseUrl("https://santiago_agent.local:3000"))
    }

    @Test
    fun agentRequest_serializesAudioAndVoiceAndSoundsCorrectly() {
        val request = AgentRequest(
            message = "Hola",
            audioInformation = "Loud voice",
            voiceFeatures = "Calm tone",
            sounds = "Footsteps"
        )
        val gson = Gson()
        val json = gson.toJson(request)
        assertTrue(json.contains("\"audio_information\":\"Loud voice\""))
        assertTrue(json.contains("\"voice_features\":\"Calm tone\""))
        assertTrue(json.contains("\"sounds\":\"Footsteps\""))
    }

    @Test
    fun agentRequest_serializesMlKitDetectionsAndVisualContextCorrectly() {
        val request = AgentRequest(
            message = "Mira esto",
            visualContext = "MLKit: Cup (92%)",
            detectedObjects = listOf("Cup (92%)"),
            mlKitDetections = listOf(
                MlKitDetectionDto(
                    label = "Cup",
                    confidence = 0.92f,
                    boundingBox = BoundingBoxDto(
                        x = 0.1f,
                        y = 0.2f,
                        width = 0.3f,
                        height = 0.4f
                    )
                )
            )
        )
        val gson = Gson()
        val json = gson.toJson(request)
        assertTrue(json.contains("\"visual_context\":\"MLKit: Cup (92%)\""))
        assertTrue(json.contains("\"detected_objects\":[\"Cup (92%)\"]"))
        assertTrue(json.contains("\"mlkit_detections\""))
        assertTrue(json.contains("\"label\":\"Cup\""))
    }

    @Test
    fun speculativeEventDto_deserializesCorrectly() {
        val json = """{"status":"observing","emotion":"curious","message":"Detectado en cámara: Cup (92%) — Analizando..."}"""
        val gson = Gson()
        val spec = gson.fromJson(json, com.example.santiagoface.data.SpeculativeEventDto::class.java)
        assertEquals("observing", spec.status)
        assertEquals("curious", spec.emotion)
        assertEquals("Detectado en cámara: Cup (92%) — Analizando...", spec.message)
    }
}
