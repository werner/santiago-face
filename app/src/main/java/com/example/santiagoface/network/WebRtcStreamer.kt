package com.example.santiagoface.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.santiagoface.logging.LogShipper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.JavaI420Buffer
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoFrame
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.TimeUnit

/**
 * WebRTC video streamer for SantiagoFace.
 * Uses PeerConnection over UDP/SRTP with hardware H.264/VP8 encoding.
 * Signaling is a single-roundtrip SDP Offer/Answer exchange via FaceHttpServer (/webrtc/offer).
 */
class WebRtcStreamer(context: Context) {

    private val eglBase: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private val videoSource: VideoSource
    private val videoTrack: VideoTrack

    @Volatile
    private var peerConnection: PeerConnection? = null

    @Volatile
    private var isConnected: Boolean = false

    @Volatile
    private var lastFramePushMs: Long = 0L

    @Volatile
    private var isDisposed: Boolean = false

    private val offerMutex = Mutex()
    private val disposeLock = Any()

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        videoSource = factory.createVideoSource(false)
        videoTrack = factory.createVideoTrack("santiago_video_track", videoSource)
        videoTrack.setEnabled(true)
    }

    suspend fun stop() = offerMutex.withLock {
        closePeerConnection()
    }

    suspend fun handleOffer(offerSdp: String): String? = offerMutex.withLock {
        if (isDisposed) return@withLock null
        closePeerConnection()

        val iceGatheringComplete = CompletableDeferred<Unit>()
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }

        val pc = synchronized(disposeLock) {
            if (isDisposed) return@withLock null
            factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.i(TAG, "ICE connection state: $state")
                    isConnected = state == PeerConnection.IceConnectionState.CONNECTED ||
                        state == PeerConnection.IceConnectionState.COMPLETED
                    if (state == PeerConnection.IceConnectionState.FAILED ||
                        state == PeerConnection.IceConnectionState.CLOSED
                    ) {
                        isConnected = false
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    if (state == PeerConnection.IceGatheringState.COMPLETE) {
                        iceGatheringComplete.complete(Unit)
                    }
                }
                override fun onIceCandidate(candidate: IceCandidate?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            })?.also {
                peerConnection = it
                it.addTrack(videoTrack, listOf("santiago_stream"))
            }
        } ?: return@withLock null

        var succeeded = false
        try {
            val setRemoteDone = CompletableDeferred<Boolean>()
            pc.setRemoteDescription(
                SimpleSdpObserver(
                    onSetSuccess = { setRemoteDone.complete(true) },
                    onSetFailure = { err ->
                        LogShipper.e(TAG, "setRemoteDescription failed: $err", details = mapOf("stage" to "setRemoteDescription"))
                        setRemoteDone.complete(false)
                    }
                ),
                SessionDescription(SessionDescription.Type.OFFER, offerSdp)
            )
            if (withTimeoutOrNull(SDP_TIMEOUT_MS) { setRemoteDone.await() } != true || isDisposed) {
                return@withLock null
            }

            val answerCreated = CompletableDeferred<SessionDescription?>()
            pc.createAnswer(
                SimpleSdpObserver(
                    onCreateSuccess = { sdp -> answerCreated.complete(sdp) },
                    onCreateFailure = { err ->
                        LogShipper.e(TAG, "createAnswer failed: $err", details = mapOf("stage" to "createAnswer"))
                        answerCreated.complete(null)
                    }
                ),
                MediaConstraints()
            )
            val answer = withTimeoutOrNull(SDP_TIMEOUT_MS) { answerCreated.await() }
            if (answer == null || isDisposed) {
                return@withLock null
            }

            val setLocalDone = CompletableDeferred<Boolean>()
            pc.setLocalDescription(
                SimpleSdpObserver(
                    onSetSuccess = { setLocalDone.complete(true) },
                    onSetFailure = { err ->
                        LogShipper.e(TAG, "setLocalDescription failed: $err", details = mapOf("stage" to "setLocalDescription"))
                        setLocalDone.complete(false)
                    }
                ),
                answer
            )
            if (withTimeoutOrNull(SDP_TIMEOUT_MS) { setLocalDone.await() } != true || isDisposed) {
                return@withLock null
            }

            // Wait up to 1.5s for host ICE candidates to be gathered into localDescription
            withTimeoutOrNull(1500) {
                iceGatheringComplete.await()
            }
            if (isDisposed) return@withLock null

            val sdp = synchronized(disposeLock) {
                if (isDisposed) null else pc.localDescription?.description
            }
            succeeded = sdp != null
            sdp
        } finally {
            if (!succeeded) {
                closePeerConnection()
            }
        }
    }

    fun pushBitmapFrame(bitmap: Bitmap) {
        if (isDisposed || peerConnection == null || bitmap.isRecycled) return
        val now = System.currentTimeMillis()
        if (now - lastFramePushMs < 40L) return // cap at ~25 FPS
        lastFramePushMs = now

        val targetW = 640
        val targetH = 480
        val scaled = if (bitmap.width != targetW || bitmap.height != targetH) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, false)
        } else {
            bitmap
        }

        try {
            val i420 = bitmapToI420(scaled)
            val timestampNs = TimeUnit.MILLISECONDS.toNanos(now)
            val frame = VideoFrame(i420, 0, timestampNs)
            try {
                synchronized(disposeLock) {
                    if (!isDisposed && peerConnection != null) {
                        videoSource.capturerObserver.onFrameCaptured(frame)
                    }
                }
            } finally {
                frame.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to push WebRTC frame: ${e.message}")
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }

    private fun closePeerConnection() {
        isConnected = false
        val pc = synchronized(disposeLock) {
            peerConnection.also { peerConnection = null }
        }
        try {
            pc?.close()
            pc?.dispose()
        } catch (_: Exception) {
        }
    }

    fun dispose() {
        synchronized(disposeLock) {
            if (isDisposed) return
            isDisposed = true
        }
        closePeerConnection()
        synchronized(disposeLock) {
            runCatching { videoTrack.dispose() }
            runCatching { videoSource.dispose() }
            runCatching { factory.dispose() }
            runCatching { eglBase.release() }
        }
    }

    private fun bitmapToI420(bitmap: Bitmap): JavaI420Buffer {
        val width = bitmap.width
        val height = bitmap.height
        val buffer = JavaI420Buffer.allocate(width, height)

        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yPlane = buffer.dataY
        val uPlane = buffer.dataU
        val vPlane = buffer.dataV
        val yStride = buffer.strideY
        val uStride = buffer.strideU
        val vStride = buffer.strideV

        var idx = 0
        for (j in 0 until height) {
            val yRowOffset = j * yStride
            val uvRowOffset = (j shr 1) * uStride
            val vRowOffset = (j shr 1) * vStride
            for (i in 0 until width) {
                val pixel = argb[idx++]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yPlane.put(yRowOffset + i, y.coerceIn(0, 255).toByte())

                if ((j and 1) == 0 && (i and 1) == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvCol = i shr 1
                    uPlane.put(uvRowOffset + uvCol, u.coerceIn(0, 255).toByte())
                    vPlane.put(vRowOffset + uvCol, v.coerceIn(0, 255).toByte())
                }
            }
        }
        return buffer
    }

    private class SimpleSdpObserver(
        private val onCreateSuccess: ((SessionDescription) -> Unit)? = null,
        private val onCreateFailure: ((String) -> Unit)? = null,
        private val onSetSuccess: (() -> Unit)? = null,
        private val onSetFailure: ((String) -> Unit)? = null
    ) : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            if (sdp != null) onCreateSuccess?.invoke(sdp)
        }
        override fun onSetSuccess() {
            onSetSuccess?.invoke()
        }
        override fun onCreateFailure(error: String?) {
            onCreateFailure?.invoke(error ?: "unknown")
        }
        override fun onSetFailure(error: String?) {
            onSetFailure?.invoke(error ?: "unknown")
        }
    }

    companion object {
        private const val TAG = "WebRtcStreamer"
        private const val SDP_TIMEOUT_MS = 5000L
    }
}
