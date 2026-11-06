package com.example.santiagoface.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.util.concurrent.TimeUnit

data class AgentRequest(
    val message: String,
    @SerializedName("audio_information")
    val audioInformation: String? = null,
    @SerializedName("voice_features")
    val voiceFeatures: String? = null,
    val sounds: String? = null,
    @SerializedName("image_base64")
    val image: String? = null,
    val images: List<String>? = null
)

data class LocationRequest(
    val latitude: Double,
    val longitude: Double
)

data class AgentResponse(
    val emotion: String? = "neutral",
    val text: String? = "",
    @SerializedName("tool_call")
    val toolCall: Any? = null // Tool calls are ignored by the app as per requirements
)

/**
 * Custom DNS resolver that maps local hostnames (such as the configured DEFAULT_URL host or .local domains)
 * to the system-resolved LAN IP, Android emulator host IP (10.0.2.2), and local loopback (127.0.0.1).
 */
object EmulatorLocalDns : Dns {
    private val defaultHost: String? by lazy {
        runCatching {
            val urlStr = com.example.santiagoface.BuildConfig.DEFAULT_URL
            val formatted = if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) urlStr else "http://$urlStr"
            java.net.URI(formatted).host
        }.getOrNull()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val isLocalHost = (defaultHost != null && hostname.equals(defaultHost, ignoreCase = true)) || hostname.endsWith(".local")

        if (isLocalHost) {
            val hostIpBytes = byteArrayOf(10, 0, 2, 2)
            val loopbackIpBytes = byteArrayOf(127, 0, 0, 1)
            val addresses = mutableListOf<InetAddress>()

            // 1. Query system DNS first to discover mDNS/LAN IPs
            try {
                val systemAddresses = Dns.SYSTEM.lookup(hostname)
                for (addr in systemAddresses) {
                    if (addr.isLoopbackAddress) {
                        addresses.add(InetAddress.getByAddress(hostname, hostIpBytes))
                    } else {
                        addresses.add(InetAddress.getByAddress(hostname, addr.address))
                    }
                }
            } catch (_: Exception) {
                // Ignore system DNS errors for .local hostnames
            }

            // 2. Always include 10.0.2.2 (Emulator Host) and 127.0.0.1 (ADB reverse / local)
            try {
                addresses.add(InetAddress.getByAddress(hostname, hostIpBytes))
                addresses.add(InetAddress.getByAddress(hostname, loopbackIpBytes))
            } catch (_: Exception) {
            }

            return addresses.distinctBy { it.hostAddress }
        }

        return Dns.SYSTEM.lookup(hostname)
    }
}

class AgentRepository(
    private val apiKey: String = com.example.santiagoface.BuildConfig.API_KEY
) {
    private val client = OkHttpClient.Builder()
        .dns(EmulatorLocalDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun Request.Builder.applyApiKey(): Request.Builder {
        if (apiKey.isNotBlank()) {
            addHeader("Authorization", "Bearer $apiKey")
            addHeader("X-API-Key", apiKey)
        }
        return this
    }

    fun cleanBaseUrl(baseUrl: String): String {
        var clean = baseUrl.trim().removeSuffix("/")
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean
    }

    /**
     * Sends a message along with optional audio info, voice features, sounds, and images to the Santiago Agent and receives the emotion and spoken text response.
     */
    suspend fun sendMessage(
        baseUrl: String,
        userMessage: String,
        audioInformation: String? = null,
        voiceFeatures: String? = null,
        sounds: String? = null,
        image: String? = null,
        images: List<String>? = null
    ): Result<AgentResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val url = "$cleanUrl/api/message"

            val jsonBody = gson.toJson(
                AgentRequest(
                    message = userMessage,
                    audioInformation = audioInformation,
                    voiceFeatures = voiceFeatures,
                    sounds = sounds,
                    image = image,
                    images = images
                )
            )
            val request = Request.Builder()
                .url(url)
                .applyApiKey()
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw Exception("Agent returned error HTTP ${response.code}: $errorBody")
                }

                val responseBody = response.body?.string()
                    ?: throw Exception("Empty response body from agent")

                val agentResponse = gson.fromJson(responseBody, AgentResponse::class.java)
                agentResponse ?: AgentResponse(emotion = "neutral", text = "Sorry, I received an empty response.")
            }
        }
    }

    /**
     * Resets the agent's conversation history.
     */
    suspend fun resetHistory(baseUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val url = "$cleanUrl/api/history/reset"

            val request = Request.Builder()
                .url(url)
                .applyApiKey()
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw Exception("HTTP ${response.code}: $errorBody")
                }
                true
            }
        }
    }

    /**
     * Checks if the Santiago Agent server is reachable via /health.
     */
    suspend fun checkHealth(baseUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val url = "$cleanUrl/api/health"

            val request = Request.Builder()
                .url(url)
                .applyApiKey()
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw Exception("HTTP ${response.code}: $errorBody")
                }
                true
            }
        }
    }

    /**
     * Sends the current location to the agent.
     */
    suspend fun sendLocation(baseUrl: String, lat: Double, lon: Double): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val url = "$cleanUrl/api/location"

            val jsonBody = gson.toJson(LocationRequest(latitude = lat, longitude = lon))
            val request = Request.Builder()
                .url(url)
                .applyApiKey()
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw Exception("HTTP ${response.code}: $errorBody")
                }
                true
            }
        }
    }
}
