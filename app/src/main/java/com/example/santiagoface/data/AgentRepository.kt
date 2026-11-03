package com.example.santiagoface.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AgentRequest(
    val message: String
)

data class AgentResponse(
    val emotion: String? = "neutral",
    val text: String? = "",
    @SerializedName("tool_call")
    val toolCall: Any? = null // Tool calls are ignored by the app as per requirements
)

class AgentRepository(
    private val apiKey: String = com.example.santiagoface.BuildConfig.API_KEY
) {
    private val client = OkHttpClient.Builder()
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

    /**
     * Sends a message to the Santiago Agent and receives the emotion and spoken text response.
     */
    suspend fun sendMessage(baseUrl: String, userMessage: String): Result<AgentResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
            val url = "$cleanBaseUrl/api/message"

            val jsonBody = gson.toJson(AgentRequest(message = userMessage))
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
            val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
            val url = "$cleanBaseUrl/api/history/reset"

            val request = Request.Builder()
                .url(url)
                .applyApiKey()
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }
    }

    /**
     * Checks if the Santiago Agent server is reachable via /health.
     */
    suspend fun checkHealth(baseUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
            val url = "$cleanBaseUrl/health"

            val request = Request.Builder()
                .url(url)
                .applyApiKey()
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }
    }
}
