package com.example.santiagoface.network

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.example.santiagoface.camera.CameraHelper
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

data class RemoteAgentResponsePayload(
    val emotion: String? = null,
    val text: String? = null
)

/**
 * Lightweight embedded HTTP server for SantiagoFace:
 * - GET /stream (or /video): Live MJPEG stream (multipart/x-mixed-replace) from CameraHelper
 * - GET /snapshot: Single JPEG frame
 * - GET /api/health: Health & status JSON
 * - POST /api/agent-response: Receives LLM agent response (emotion + text) from SantiagoRemote
 */
class FaceHttpServer(
    private val port: Int = 8080,
    private val cameraHelper: CameraHelper,
    private val currentEmotionProvider: () -> String,
    private val onRemoteAgentResponse: (emotion: String?, text: String?) -> Unit
) {
    private val gson = Gson()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(port).also { it.reuseAddress = true }
                serverSocket = socket
                Log.i(TAG, "FaceHttpServer listening on port $port")

                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FaceHttpServer on port $port: ${e.message}")
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val headerText = readHeaders(input) ?: return
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val idx = lines[i].indexOf(':')
                if (idx > 0) {
                    val key = lines[i].substring(0, idx).trim().lowercase()
                    val value = lines[i].substring(idx + 1).trim()
                    headers[key] = value
                }
            }

            when {
                method == "GET" && (path == "/stream" || path == "/video") -> {
                    streamMjpeg(socket, output)
                }
                method == "GET" && path == "/snapshot" -> {
                    val jpeg = cameraHelper.getLatestJpegBytes(targetWidth = 640, targetHeight = 480, quality = 75)
                    if (jpeg != null) {
                        val respHeaders = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\nConnection: close\r\n\r\n"
                        output.write(respHeaders.toByteArray(StandardCharsets.US_ASCII))
                        output.write(jpeg)
                        output.flush()
                    } else {
                        writeJsonResponse(output, 503, "Service Unavailable", """{"status":"error","message":"Camera not active"}""")
                    }
                }
                method == "GET" && path == "/api/health" -> {
                    val body = gson.toJson(
                        mapOf(
                            "status" to "ok",
                            "app" to "SantiagoFace",
                            "camera_active" to cameraHelper.isCameraActive.value,
                            "emotion" to currentEmotionProvider()
                        )
                    )
                    writeJsonResponse(output, 200, "OK", body)
                }
                method == "POST" && (path == "/api/agent-response" || path == "/api/response") -> {
                    val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 1_048_576) ?: 0
                    val bodyBytes = if (contentLength > 0) readExactBytes(input, contentLength) else ByteArray(0)
                    val bodyStr = String(bodyBytes, StandardCharsets.UTF_8)
                    val payload = runCatching {
                        gson.fromJson(bodyStr, RemoteAgentResponsePayload::class.java)
                    }.getOrNull()

                    if (payload == null) {
                        writeJsonResponse(output, 400, "Bad Request", """{"status":"error","message":"Invalid JSON"}""")
                    } else {
                        onRemoteAgentResponse(payload.emotion, payload.text)
                        writeJsonResponse(output, 200, "OK", """{"status":"ok"}""")
                    }
                }
                else -> {
                    writeJsonResponse(output, 404, "Not Found", """{"status":"error","message":"Not found"}""")
                }
            }
        } catch (_: Exception) {
            // Client disconnected or timed out
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun streamMjpeg(socket: Socket, output: OutputStream) {
        val boundary = "frame"
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n")
            append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
            append("Pragma: no-cache\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.flush()

        while (!socket.isClosed) {
            val jpeg = cameraHelper.getLatestJpegBytes(targetWidth = 640, targetHeight = 480, quality = 65)
            if (jpeg != null) {
                writeMjpegFrame(output, boundary, jpeg)
                delay(66) // ~15 FPS
            } else {
                writeMjpegFrame(output, boundary, getPlaceholderJpeg())
                delay(1000) // 1 FPS keep-alive while camera is off
            }
        }
    }

    private fun writeMjpegFrame(output: OutputStream, boundary: String, jpeg: ByteArray) {
        val partHeader = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
        output.write(partHeader.toByteArray(StandardCharsets.US_ASCII))
        output.write(jpeg)
        output.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private var cachedPlaceholderJpeg: ByteArray? = null

    private fun getPlaceholderJpeg(): ByteArray {
        cachedPlaceholderJpeg?.let { return it }
        val bmp = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(10, 14, 23))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SantiagoFace Camera Off", 240f, 170f, paint)
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 18f
        canvas.drawText("Enable camera on Face app", 240f, 205f, paint)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bmp.recycle()
        return out.toByteArray().also { cachedPlaceholderJpeg = it }
    }

    private fun writeJsonResponse(output: OutputStream, code: Int, reason: String, json: String) {
        val bodyBytes = json.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 $code $reason\r\nContent-Type: application/json\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\n\r\n"
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }

    private fun readHeaders(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matchState = 0
        while (buffer.size() < 16384) {
            val b = input.read()
            if (b == -1) return null
            buffer.write(b)
            matchState = when {
                b == '\r'.code && (matchState == 0 || matchState == 2) -> matchState + 1
                b == '\n'.code && (matchState == 1 || matchState == 3) -> matchState + 1
                else -> 0
            }
            if (matchState == 4) {
                return buffer.toString(StandardCharsets.US_ASCII.name())
            }
        }
        return null
    }

    private fun readExactBytes(input: BufferedInputStream, length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(result, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) result else result.copyOf(offset)
    }

    companion object {
        private const val TAG = "FaceHttpServer"

        fun getLocalIpAddress(): String? {
            return runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { intf ->
                    intf.inetAddresses.toList()
                }?.firstOrNull { addr ->
                    !addr.isLoopbackAddress && addr is Inet4Address
                }?.hostAddress
            }.getOrNull()
        }
    }
}
