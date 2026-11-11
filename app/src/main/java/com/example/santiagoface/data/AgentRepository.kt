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

data class AgentNameResponse(
    @SerializedName("agent_name")
    val agentName: String? = null
)

data class SpeculativeEventDto(
    val status: String? = "thinking",
    val emotion: String? = "neutral",
    val message: String? = "Pensando..."
)

data class MlKitDetectionDto(
    val label: String,
    val confidence: Float,
    @SerializedName("bounding_box")
    val boundingBox: BoundingBoxDto? = null
)

data class AgentRequest(
    val message: String,
    @SerializedName("audio_information")
    val audioInformation: String? = null,
    @SerializedName("voice_features")
    val voiceFeatures: String? = null,
    val sounds: String? = null,
    @SerializedName("image_base64")
    val image: String? = null,
    val images: List<String>? = null,
    val goal: String? = null,               // "approach" | "search" | "avoid" | "align" | "observe"
    val target: String? = null,             // e.g. "person_werner", "red_mug"
    @SerializedName("reference_size_cm")
    val referenceSizeCm: Float? = null,     // e.g. 22.0f for face, 15.0f for mug
    @SerializedName("target_distance_cm")
    val targetDistanceCm: Float? = null,    // e.g. 25.0f
    @SerializedName("camera_fov_deg")
    val cameraFovDeg: Float? = null,        // e.g. 62.5f
    @SerializedName("auto_act")
    val autoAct: Boolean? = null,
    @SerializedName("visual_context")
    val visualContext: String? = null,
    @SerializedName("detected_objects")
    val detectedObjects: List<String>? = null,
    @SerializedName("mlkit_detections")
    val mlKitDetections: List<MlKitDetectionDto>? = null
)

data class BoundingBoxDto(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    @SerializedName("center_x")
    val centerX: Float = 0.5f,
    @SerializedName("center_y")
    val centerY: Float = 0.5f,
    @SerializedName("area_ratio")
    val areaRatio: Float = 0f
)

data class VisualMatchDto(
    val id: Long = 0,
    val key: String? = null,
    val category: String = "general",
    val content: String = "",
    @SerializedName("has_image")
    val hasImage: Boolean = false,
    val similarity: Float? = null,
    @SerializedName("estimated_distance_cm")
    val estimatedDistanceCm: Float? = null,
    @SerializedName("distance_zone")
    val distanceZone: String? = null,
    val bearing: String? = null,
    @SerializedName("recommended_action")
    val recommendedAction: String? = null,
    @SerializedName("created_at")
    val createdAt: Long? = null
)

data class VisionAnalysisDto(
    @SerializedName("subject_visible")
    val subjectVisible: Boolean = false,
    @SerializedName("detected_category")
    val detectedCategory: String = "none",
    @SerializedName("face_detected")
    val faceDetected: Boolean = false,
    @SerializedName("face_confidence")
    val faceConfidence: Float = 0f,
    @SerializedName("bounding_box")
    val boundingBox: BoundingBoxDto? = null,
    @SerializedName("estimated_distance_cm")
    val estimatedDistanceCm: Float? = null,
    @SerializedName("estimated_distance_m")
    val estimatedDistanceM: Float? = null,
    @SerializedName("distance_zone")
    val distanceZone: String = "not_visible",
    val bearing: String = "none",
    @SerializedName("bearing_degrees")
    val bearingDegrees: Float = 0f,
    @SerializedName("horizontal_offset")
    val horizontalOffset: Float? = null,
    @SerializedName("closing_rate")
    val closingRate: Float? = null,
    @SerializedName("target_query")
    val targetQuery: String? = null,
    @SerializedName("target_found")
    val targetFound: Boolean = false,
    @SerializedName("recognized_key")
    val recognizedKey: String? = null,
    @SerializedName("recognized_content")
    val recognizedContent: String? = null,
    @SerializedName("recognition_similarity")
    val recognitionSimilarity: Float? = null,
    @SerializedName("navigation_behavior")
    val navigationBehavior: String = "observe",
    @SerializedName("recommended_action")
    val recommendedAction: String = "stop",
    @SerializedName("recommended_speed")
    val recommendedSpeed: Int? = null,
    @SerializedName("action_reason")
    val actionReason: String = ""
)

data class AgentResponse(
    val emotion: String? = "neutral",
    val text: String? = "",
    @SerializedName("tool_call")
    val toolCall: Any? = null,
    @SerializedName("esp32_response")
    val esp32Response: String? = null,
    @SerializedName("esp32_error")
    val esp32Error: String? = null,
    @SerializedName("visual_matches")
    val visualMatches: List<VisualMatchDto>? = null,
    @SerializedName("vision_analysis")
    val visionAnalysis: VisionAnalysisDto? = null,
    @SerializedName("request_image")
    val requestImage: Boolean? = null
)

data class VisionActRequest(
    @SerializedName("image_base64")
    val image: String? = null,
    val images: List<String>? = null,
    val goal: String? = null,               // "approach" | "search" | "avoid" | "align" | "observe"
    val target: String? = null,             // e.g. "person_werner", "red_mug"
    val category: String? = null,           // "person" | "object"
    @SerializedName("scan_direction")
    val scanDirection: String? = null,      // "right" | "left"
    @SerializedName("reference_size_cm")
    val referenceSizeCm: Float? = null,
    @SerializedName("target_distance_cm")
    val targetDistanceCm: Float? = null,
    @SerializedName("camera_fov_deg")
    val cameraFovDeg: Float? = null,
    @SerializedName("execute_hardware")
    val executeHardware: Boolean? = true,
    @SerializedName("detected_objects")
    val detectedObjects: List<String>? = null,
    @SerializedName("mlkit_detections")
    val mlKitDetections: List<MlKitDetectionDto>? = null,
    @SerializedName("target_detected_locally")
    val targetDetectedLocally: Boolean? = null
)

data class VisionActResponse(
    @SerializedName("navigation_behavior")
    val navigationBehavior: String = "observe",
    @SerializedName("executed_action")
    val executedAction: String = "stop",
    @SerializedName("executed_speed")
    val executedSpeed: Int? = null,
    @SerializedName("target_found")
    val targetFound: Boolean = false,
    @SerializedName("action_reason")
    val actionReason: String = "",
    @SerializedName("vision_analysis")
    val visionAnalysis: VisionAnalysisDto? = null,
    @SerializedName("visual_matches")
    val visualMatches: List<VisualMatchDto>? = null,
    @SerializedName("esp32_response")
    val esp32Response: String? = null,
    @SerializedName("esp32_error")
    val esp32Error: String? = null
)

data class LocationRequest(
    val latitude: Double,
    val longitude: Double
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
     * Sends a message along with optional audio info, voice features, sounds, images, ML Kit detections, and vision targets to the Santiago Agent.
     */
    suspend fun sendMessage(
        baseUrl: String,
        userMessage: String,
        audioInformation: String? = null,
        voiceFeatures: String? = null,
        sounds: String? = null,
        image: String? = null,
        images: List<String>? = null,
        goal: String? = null,
        target: String? = null,
        referenceSizeCm: Float? = null,
        targetDistanceCm: Float? = null,
        cameraFovDeg: Float? = null,
        autoAct: Boolean? = null,
        visualContext: String? = null,
        detectedObjects: List<String>? = null,
        mlKitDetections: List<MlKitDetectionDto>? = null
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
                    images = images,
                    goal = goal,
                    target = target,
                    referenceSizeCm = referenceSizeCm,
                    targetDistanceCm = targetDistanceCm,
                    cameraFovDeg = cameraFovDeg,
                    autoAct = autoAct,
                    visualContext = visualContext,
                    detectedObjects = detectedObjects,
                    mlKitDetections = mlKitDetections
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
     * Sends a message via Server-Sent Events (/api/message/stream) to receive real-time speculative thoughts/emotions
     * immediately while the LLM generates the complete final answer.
     */
    suspend fun sendMessageStream(
        baseUrl: String,
        userMessage: String,
        audioInformation: String? = null,
        voiceFeatures: String? = null,
        sounds: String? = null,
        image: String? = null,
        images: List<String>? = null,
        goal: String? = null,
        target: String? = null,
        referenceSizeCm: Float? = null,
        targetDistanceCm: Float? = null,
        cameraFovDeg: Float? = null,
        autoAct: Boolean? = null,
        visualContext: String? = null,
        detectedObjects: List<String>? = null,
        mlKitDetections: List<MlKitDetectionDto>? = null,
        onSpeculative: ((SpeculativeEventDto) -> Unit)? = null
    ): Result<AgentResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val url = "$cleanUrl/api/message/stream"

            val jsonBody = gson.toJson(
                AgentRequest(
                    message = userMessage,
                    audioInformation = audioInformation,
                    voiceFeatures = voiceFeatures,
                    sounds = sounds,
                    image = image,
                    images = images,
                    goal = goal,
                    target = target,
                    referenceSizeCm = referenceSizeCm,
                    targetDistanceCm = targetDistanceCm,
                    cameraFovDeg = cameraFovDeg,
                    autoAct = autoAct,
                    visualContext = visualContext,
                    detectedObjects = detectedObjects,
                    mlKitDetections = mlKitDetections
                )
            )
            val request = Request.Builder()
                .url(url)
                .applyApiKey()
                .addHeader("Accept", "text/event-stream")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw Exception("Agent returned error HTTP ${response.code}: $errorBody")
                }

                val source = response.body?.source() ?: throw Exception("Empty response body from stream")
                var currentEvent: String? = null
                var finalResponse: AgentResponse? = null
                var errorEventJson: String? = null

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("event:")) {
                        currentEvent = line.substringAfter("event:").trim()
                    } else if (line.startsWith("data:")) {
                        val data = line.substringAfter("data:").trim()
                        when (currentEvent) {
                            "speculative" -> {
                                try {
                                    val spec = gson.fromJson(data, SpeculativeEventDto::class.java)
                                    if (spec != null) {
                                        onSpeculative?.invoke(spec)
                                    }
                                } catch (_: Exception) {}
                            }
                            "response" -> {
                                finalResponse = gson.fromJson(data, AgentResponse::class.java)
                            }
                            "error" -> {
                                errorEventJson = data
                            }
                        }
                    }
                }

                finalResponse ?: errorEventJson?.let {
                    throw Exception("Agent stream error: $it")
                } ?: throw Exception("Stream finished without final response")
            }
        }.recoverCatching {
            // Seamless fallback to unary POST /api/message if streaming fails or is interrupted
            sendMessage(
                baseUrl = baseUrl,
                userMessage = userMessage,
                audioInformation = audioInformation,
                voiceFeatures = voiceFeatures,
                sounds = sounds,
                image = image,
                images = images,
                goal = goal,
                target = target,
                referenceSizeCm = referenceSizeCm,
                targetDistanceCm = targetDistanceCm,
                cameraFovDeg = cameraFovDeg,
                autoAct = autoAct,
                visualContext = visualContext,
                detectedObjects = detectedObjects,
                mlKitDetections = mlKitDetections
            ).getOrThrow()
        }
    }

    /**
     * Sends a fast autonomous vision action request (POST /api/vision/act) to Santiago Agent.
     */
    suspend fun sendVisionAct(
        baseUrl: String,
        image: String? = null,
        images: List<String>? = null,
        goal: String? = null,
        target: String? = null,
        category: String? = null,
        scanDirection: String? = null,
        referenceSizeCm: Float? = null,
        targetDistanceCm: Float? = null,
        cameraFovDeg: Float? = null,
        executeHardware: Boolean? = true,
        detectedObjects: List<String>? = null,
        mlKitDetections: List<MlKitDetectionDto>? = null,
        targetDetectedLocally: Boolean? = null
    ): Result<VisionActResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val url = "$cleanUrl/api/vision/act"

            val jsonBody = gson.toJson(
                VisionActRequest(
                    image = image,
                    images = images,
                    goal = goal,
                    target = target,
                    category = category,
                    scanDirection = scanDirection,
                    referenceSizeCm = referenceSizeCm,
                    targetDistanceCm = targetDistanceCm,
                    cameraFovDeg = cameraFovDeg,
                    executeHardware = executeHardware,
                    detectedObjects = detectedObjects,
                    mlKitDetections = mlKitDetections,
                    targetDetectedLocally = targetDetectedLocally
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
                    throw Exception("Vision act returned error HTTP ${response.code}: $errorBody")
                }

                val responseBody = response.body?.string()
                    ?: throw Exception("Empty response body from vision act")

                val visionActResponse = gson.fromJson(responseBody, VisionActResponse::class.java)
                visionActResponse ?: VisionActResponse()
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

    /**
     * Fetches the agent's name from /api/name.
     */
    suspend fun fetchAgentName(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = cleanBaseUrl(baseUrl)
            val url = "$cleanUrl/api/name"

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

                val responseBody = response.body?.string()
                    ?: throw Exception("Empty response body from agent name API")

                val nameResponse = gson.fromJson(responseBody, AgentNameResponse::class.java)
                nameResponse?.agentName ?: ""
            }
        }
    }
}
