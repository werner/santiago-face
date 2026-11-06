package com.example.santiagoface

import com.example.santiagoface.data.AgentRepository
import org.junit.Assert.assertEquals
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
        val request = com.example.santiagoface.data.AgentRequest(
            message = "Hola",
            audioInformation = "Loud voice",
            voiceFeatures = "Calm tone",
            sounds = "Footsteps"
        )
        val gson = com.google.gson.Gson()
        val json = gson.toJson(request)
        org.junit.Assert.assertTrue(json.contains("\"audio_information\":\"Loud voice\""))
        org.junit.Assert.assertTrue(json.contains("\"voice_features\":\"Calm tone\""))
        org.junit.Assert.assertTrue(json.contains("\"sounds\":\"Footsteps\""))
    }
}
