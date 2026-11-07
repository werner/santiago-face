package com.example.santiagoface.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class CameraHelper(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _latestFrameBase64 = MutableStateFlow<String?>(null)
    val latestFrameBase64: StateFlow<String?> = _latestFrameBase64.asStateFlow()

    private val _cameraFovDeg = MutableStateFlow(60.0f)
    val cameraFovDeg: StateFlow<Float> = _cameraFovDeg.asStateFlow()

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    @Volatile
    private var currentBitmap: Bitmap? = null
    private val bitmapLock = Any()

    init {
        updateFov()
    }

    fun setFrontCamera(isFront: Boolean) {
        _isFrontCamera.value = isFront
        updateFov()
    }

    private fun updateFov() {
        _cameraFovDeg.value = calculateCameraFov(context, _isFrontCamera.value)
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewSurfaceProvider: Preview.SurfaceProvider? = null,
        isFront: Boolean = true
    ) {
        _isFrontCamera.value = isFront
        updateFov()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val cameraSelector = if (_isFrontCamera.value) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    processImageFrame(imageProxy)
                }

                cameraProvider?.unbindAll()

                if (previewSurfaceProvider != null) {
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewSurfaceProvider)
                    }
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } else {
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageAnalysis
                    )
                }

                _isCameraActive.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _isCameraActive.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        _isCameraActive.value = false
        synchronized(bitmapLock) {
            currentBitmap?.recycle()
            currentBitmap = null
        }
        _latestFrameBase64.value = null
    }

    private fun processImageFrame(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val matrix = Matrix()
            // 1. Orientation rotation (Critical: upright 0°)
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees.toFloat())
            }
            // 2. Front Camera Mirroring: Horizontally flip if using front camera
            if (_isFrontCamera.value) {
                matrix.postScale(-1f, 1f)
            }

            val processedBitmap = if (!matrix.isIdentity) {
                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
            } else {
                bitmap
            }

            synchronized(bitmapLock) {
                if (currentBitmap != processedBitmap && currentBitmap != bitmap) {
                    currentBitmap?.recycle()
                }
                currentBitmap = processedBitmap
            }

            // Also keep latest Base64 cached for quick access
            val base64 = bitmapToBase64(processedBitmap)
            _latestFrameBase64.value = base64

            if (processedBitmap != bitmap) {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Captures a single current camera frame processed as Base64 JPEG (320x240 or 640x480, quality=80).
     */
    fun getSingleFrameBase64(
        targetWidth: Int = 640,
        targetHeight: Int = 480,
        quality: Int = 80
    ): String? {
        val bmpCopy = synchronized(bitmapLock) {
            currentBitmap?.takeIf { !it.isRecycled }?.let {
                Bitmap.createBitmap(it)
            }
        } ?: return _latestFrameBase64.value

        val base64 = bitmapToBase64(bmpCopy, targetWidth, targetHeight, quality)
        bmpCopy.recycle()
        return base64
    }

    /**
     * Captures 2 sequential frames ~300ms apart (for closing_rate computation on server).
     */
    suspend fun getSequentialFramesBase64(
        delayMs: Long = 300L,
        targetWidth: Int = 640,
        targetHeight: Int = 480,
        quality: Int = 80
    ): List<String> = withContext(Dispatchers.Default) {
        val frame1 = getSingleFrameBase64(targetWidth, targetHeight, quality)
        delay(delayMs)
        val frame2 = getSingleFrameBase64(targetWidth, targetHeight, quality)

        val list = mutableListOf<String>()
        if (frame1 != null) list.add(frame1)
        if (frame2 != null) list.add(frame2)
        list
    }

    /**
     * Converts a bitmap to a JPEG quality=80 Base64.NO_WRAP string.
     */
    private fun bitmapToBase64(
        bitmap: Bitmap,
        targetWidth: Int = 640,
        targetHeight: Int = 480,
        quality: Int = 80
    ): String? {
        try {
            if (bitmap.isRecycled) return null

            val scaledBitmap = if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val byteArray = outputStream.toByteArray()

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }

            return Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    companion object {
        /**
         * Calculates horizontal field of view in degrees using CameraCharacteristics:
         * FOV_deg = 2 * arctan( w_sensor / (2 * f_focal) ) * (180 / PI)
         */
        fun calculateCameraFov(context: Context, isFrontCamera: Boolean): Float {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    ?: return 60.0f
                val targetLensFacing = if (isFrontCamera) {
                    CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    CameraCharacteristics.LENS_FACING_BACK
                }

                for (cameraId in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (facing == targetLensFacing) {
                        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

                        if (focalLengths != null && focalLengths.isNotEmpty() && physicalSize != null) {
                            val focalLength = focalLengths[0]
                            val sensorWidth = if (sensorOrientation == 90 || sensorOrientation == 270) {
                                physicalSize.height
                            } else {
                                physicalSize.width
                            }

                            if (focalLength > 0f && sensorWidth > 0f) {
                                val fovRad = 2.0 * Math.atan((sensorWidth / (2.0f * focalLength)).toDouble())
                                val fovDeg = Math.toDegrees(fovRad).toFloat()
                                if (fovDeg in 30.0f..120.0f) {
                                    return fovDeg
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return 60.0f
        }
    }
}
