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
}
