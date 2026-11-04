package com.example.santiagoface

import com.example.santiagoface.data.EmulatorLocalDns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorLocalDnsTest {

    @Test
    fun lookup_santiagoAgentLocal_includesHostIpFallbackAndPreservesHostname() {
        val addresses = EmulatorLocalDns.lookup("santiago_agent.local")
        assertTrue("Addresses list should not be empty", addresses.isNotEmpty())
        assertEquals("santiago_agent.local", addresses[0].hostName)
        val hostAddresses = addresses.map { it.hostAddress }
        assertTrue("Should include emulator host IP 10.0.2.2", hostAddresses.contains("10.0.2.2"))
    }

    @Test
    fun lookup_genericDotLocal_includesHostIpFallbackAndPreservesHostname() {
        val addresses = EmulatorLocalDns.lookup("custom_service.local")
        assertTrue("Addresses list should not be empty", addresses.isNotEmpty())
        assertEquals("custom_service.local", addresses[0].hostName)
        val hostAddresses = addresses.map { it.hostAddress }
        assertTrue("Should include emulator host IP 10.0.2.2", hostAddresses.contains("10.0.2.2"))
    }

    @Test
    fun lookup_defaultUrlHost_includesHostIpFallbackAndPreservesHostname() {
        val defaultUrlStr = BuildConfig.DEFAULT_URL
        val formattedUrl = if (defaultUrlStr.startsWith("http://") || defaultUrlStr.startsWith("https://")) defaultUrlStr else "http://$defaultUrlStr"
        val host = java.net.URI(formattedUrl).host ?: "10.0.2.2"

        val addresses = EmulatorLocalDns.lookup(host)
        assertTrue("Addresses list should not be empty", addresses.isNotEmpty())
        assertEquals(host, addresses[0].hostName)
        val hostAddresses = addresses.map { it.hostAddress }
        assertTrue("Should include emulator host IP 10.0.2.2", hostAddresses.contains("10.0.2.2"))
    }
}
