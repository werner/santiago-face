package com.example.santiagoface.camera

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.santiagoface.data.BoundingBoxDto
import com.example.santiagoface.data.MlKitDetectionDto
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBoxDto? = null,
    val trackingId: Int? = null,
    val centerXNorm: Float = boundingBox?.let { (it.centerX * 2f - 1f).coerceIn(-1f, 1f) } ?: 0f,
    val areaRatio: Float = boundingBox?.areaRatio?.coerceIn(0f, 1f) ?: 0f
) {
    fun toDto(): MlKitDetectionDto {
        return MlKitDetectionDto(
            label = label,
            confidence = confidence,
            boundingBox = boundingBox
        )
    }
}

class MlKitVisionHelper {

    private val objectDetector: ObjectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    private val imageLabeler: ImageLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.45f)
            .build()
        ImageLabeling.getClient(options)
    }

    private val _detectedObjects = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detectedObjects: StateFlow<List<DetectedObject>> = _detectedObjects.asStateFlow()

    private val isProcessing = AtomicBoolean(false)
    private var lastAnalysisTimestamp = 0L
    @Volatile
    private var highFrequencyMode = false

    private val throttleIntervalMs: Long
        get() = if (highFrequencyMode) 65L else 350L // ~15 Hz in search mode, ~3 Hz idle

    fun setHighFrequencyMode(enabled: Boolean) {
        highFrequencyMode = enabled
    }

    /**
     * Processes a bitmap asynchronously, running ML Kit object detection & image labeling.
     * Throttled to preserve battery and prevent CPU spikes.
     */
    fun processBitmapThrottled(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTimestamp < throttleIntervalMs) {
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            return
        }
        lastAnalysisTimestamp = now

        try {
            if (bitmap.isRecycled) {
                isProcessing.set(false)
                return
            }

            // Create a small, safe copy if needed or use directly
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val bmpWidth = bitmap.width.toFloat()
            val bmpHeight = bitmap.height.toFloat()

            // 1. Run Object Detector
            objectDetector.process(inputImage)
                .addOnSuccessListener { detectedObjectsList ->
                    val results = mutableListOf<DetectedObject>()

                    for (obj in detectedObjectsList) {
                        val bounds = obj.boundingBox
                        val normalizedBox = normalizeBoundingBox(bounds, bmpWidth, bmpHeight)
                        val primaryLabel = obj.labels.maxByOrNull { it.confidence }
                        val labelText = primaryLabel?.text ?: "Object"
                        val conf = primaryLabel?.confidence ?: 0.5f

                        results.add(
                            DetectedObject(
                                label = labelText,
                                confidence = conf,
                                boundingBox = normalizedBox,
                                trackingId = obj.trackingId
                            )
                        )
                    }

                    // 2. Also run Image Labeler for fine-grained semantic labels (e.g. Cup, Bottle, Person, Chair)
                    imageLabeler.process(inputImage)
                        .addOnSuccessListener { labels ->
                            mergeImageLabelsWithBoxes(results, labels.map { it.text to it.confidence })
                            // Keep sorted by confidence descending
                            results.sortByDescending { it.confidence }
                            _detectedObjects.value = results.take(10)
                            isProcessing.set(false)
                        }
                        .addOnFailureListener {
                            _detectedObjects.value = results.take(10)
                            isProcessing.set(false)
                        }
                }
                .addOnFailureListener {
                    isProcessing.set(false)
                }
        } catch (e: Exception) {
            e.printStackTrace()
            isProcessing.set(false)
        }
    }

    /**
     * Synchronous / suspending direct detection on a bitmap for immediate querying.
     */
    suspend fun analyzeBitmapNow(bitmap: Bitmap): List<DetectedObject> = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled) return@withContext emptyList()
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val bmpWidth = bitmap.width.toFloat()
            val bmpHeight = bitmap.height.toFloat()

            val objTask = objectDetector.process(inputImage)
            val labelTask = imageLabeler.process(inputImage)

            Tasks.await(objTask)
            Tasks.await(labelTask)

            val results = mutableListOf<DetectedObject>()
            for (obj in objTask.result) {
                val normalizedBox = normalizeBoundingBox(obj.boundingBox, bmpWidth, bmpHeight)
                val primaryLabel = obj.labels.maxByOrNull { it.confidence }
                results.add(
                    DetectedObject(
                        label = primaryLabel?.text ?: "Object",
                        confidence = primaryLabel?.confidence ?: 0.5f,
                        boundingBox = normalizedBox,
                        trackingId = obj.trackingId
                    )
                )
            }

            mergeImageLabelsWithBoxes(results, labelTask.result.map { it.text to it.confidence })

            results.sortByDescending { it.confidence }
            val finalResult = results.take(10)
            _detectedObjects.value = finalResult
            finalResult
        } catch (e: Exception) {
            e.printStackTrace()
            _detectedObjects.value
        }
    }

    /**
     * Converts an Android Rect into normalized BoundingBoxDto [0..1]
     */
    private fun normalizeBoundingBox(rect: Rect, imgWidth: Float, imgHeight: Float): BoundingBoxDto {
        if (imgWidth <= 0f || imgHeight <= 0f) return BoundingBoxDto()
        val xNorm = (rect.left.toFloat() / imgWidth).coerceIn(0f, 1f)
        val yNorm = (rect.top.toFloat() / imgHeight).coerceIn(0f, 1f)
        val wNorm = (rect.width().toFloat() / imgWidth).coerceIn(0f, 1f)
        val hNorm = (rect.height().toFloat() / imgHeight).coerceIn(0f, 1f)
        val cx = (xNorm + wNorm / 2f).coerceIn(0f, 1f)
        val cy = (yNorm + hNorm / 2f).coerceIn(0f, 1f)
        val area = (wNorm * hNorm).coerceIn(0f, 1f)

        return BoundingBoxDto(
            x = xNorm,
            y = yNorm,
            width = wNorm,
            height = hNorm,
            centerX = cx,
            centerY = cy,
            areaRatio = area
        )
    }

    /**
     * Generates a concise summary string of detected objects for visual context metadata.
     */
    fun getSummaryString(): String {
        val current = _detectedObjects.value
        if (current.isEmpty()) return ""
        return current.take(5).joinToString(", ") {
            "${it.label} (${(it.confidence * 100).toInt()}%)"
        }
    }

    private fun mergeImageLabelsWithBoxes(
        results: MutableList<DetectedObject>,
        labels: List<Pair<String, Float>>
    ) {
        val genericBoxFallback = results
            .filter { it.boundingBox != null && it.label.lowercase() in GENERIC_OBJECT_LABELS }
            .maxByOrNull { it.areaRatio }
            ?.boundingBox

        for ((labelText, labelConf) in labels) {
            val existingIndex = results.indexOfFirst {
                it.label.equals(labelText, ignoreCase = true)
            }
            if (existingIndex >= 0) {
                val existing = results[existingIndex]
                if (labelConf > existing.confidence) {
                    results[existingIndex] = existing.copy(confidence = labelConf)
                }
            } else {
                val box = genericBoxFallback
                results.add(
                    DetectedObject(
                        label = labelText,
                        confidence = labelConf,
                        boundingBox = box,
                        centerXNorm = box?.let { (it.centerX * 2f - 1f).coerceIn(-1f, 1f) } ?: 0f,
                        areaRatio = box?.areaRatio?.coerceIn(0f, 1f) ?: 0f
                    )
                )
            }
        }
    }

    /**
     * Checks if a target object is detected by ML Kit, supporting COCO/ML Kit label aliases.
     */
    fun findTarget(targetQuery: String, minConfidence: Float = 0.50f): DetectedObject? {
        val normalizedQuery = targetQuery.trim().lowercase()
        val aliases = LABEL_ALIASES[normalizedQuery] ?: listOf(normalizedQuery)
        return _detectedObjects.value
            .filter { detection ->
                detection.confidence >= minConfidence && aliases.any { alias ->
                    val detLabel = detection.label.lowercase()
                    detLabel == alias || detLabel.contains(alias) || alias.contains(detLabel)
                }
            }
            .maxByOrNull { it.confidence }
    }

    fun clear() {
        _detectedObjects.value = emptyList()
    }

    companion object {
        private val GENERIC_OBJECT_LABELS = setOf(
            "object", "home good", "food", "fashion good", "place", "plant"
        )

        private val LABEL_ALIASES = mapOf(
            "cup" to listOf("cup", "mug", "coffee cup", "teacup", "tableware"),
            "bottle" to listOf("bottle", "water bottle", "drinkware"),
            "cell phone" to listOf("cell phone", "mobile phone", "telephone", "smartphone", "phone"),
            "person" to listOf("person", "human", "man", "woman", "face", "people"),
            "dining table" to listOf("dining table", "table", "desk"),
            "tv" to listOf("tv", "television", "monitor", "screen"),
            "laptop" to listOf("laptop", "computer", "notebook"),
            "potted plant" to listOf("potted plant", "plant", "houseplant"),
            "sports ball" to listOf("sports ball", "ball"),
            "couch" to listOf("couch", "sofa")
        )
    }
}
