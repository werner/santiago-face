package com.example.santiagoface.data

import com.example.santiagoface.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class Esp32MoveCommand(
    val action: String,
    val speed: Int? = null,
    @SerializedName("duration_ms")
    val durationMs: Long? = null
)

data class Esp32MoveResult(
    val status: String = "error",
    val action: String? = null,
    val reason: String? = null,
    @SerializedName("distance_cm")
    val distanceCm: Float? = null,
    val message: String? = null
) {
    val isOk: Boolean get() = status.equals("ok", ignoreCase = true)
    val isBlocked: Boolean get() = status.equals("blocked", ignoreCase = true)
    val isError: Boolean get() = !isOk && !isBlocked
    val isObstacleAhead: Boolean get() = isBlocked && reason == "obstacle_ahead"
    val isObstacleBehind: Boolean get() = isBlocked && reason == "obstacle_behind"
}

data class Esp32StatusResult(
    @SerializedName("obstacle_blocked")
    val obstacleBlocked: Boolean = false,
    @SerializedName("forward_blocked")
    val forwardBlocked: Boolean = false,
    @SerializedName("backward_blocked")
    val backwardBlocked: Boolean = false,
    @SerializedName("distance_cm")
    val distanceCm: Float? = null,
    @SerializedName("rear_distance_cm")
    val rearDistanceCm: Float? = null,
    val state: String = "stop"
)

/**
 * Low-latency HTTP client for direct Android <-> ESP32 motor & sensor communication.
 */
class Esp32Client(
    private val apiKey: String = BuildConfig.ESP32_API_KEY
) {
    private val client = OkHttpClient.Builder()
        .dns(EmulatorLocalDns)
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .writeTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun cleanBaseUrl(rawUrl: String): String {
        var clean = rawUrl.trim().removeSuffix("/")
        if (clean.isNotEmpty() && !clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean
    }

    suspend fun move(
        baseUrl: String,
        action: String,
        speed: Int? = null,
        durationMs: Long? = null
    ): Esp32MoveResult = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val payload = gson.toJson(Esp32MoveCommand(action = action, speed = speed, durationMs = durationMs))
            val request = Request.Builder()
                .url("$cleanUrl/api/move")
                .addHeader("X-API-Key", apiKey)
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    runCatching { gson.fromJson(body, Esp32MoveResult::class.java) }.getOrNull()
                        ?: Esp32MoveResult(status = "error", message = "Invalid JSON: $body")
                } else {
                    Esp32MoveResult(status = if (response.isSuccessful) "ok" else "error")
                }
            }
        } catch (e: Exception) {
            Esp32MoveResult(status = "error", message = e.message ?: "ESP32 connection error")
        }
    }

    suspend fun getStatus(baseUrl: String): Esp32StatusResult? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val request = Request.Builder()
                .url("$cleanUrl/api/status")
                .addHeader("X-API-Key", apiKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                runCatching { gson.fromJson(body, Esp32StatusResult::class.java) }.getOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }
}
