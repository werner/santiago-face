package com.example.santiagoface.ui.face

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.santiagoface.ui.theme.SantiagoFaceTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun RobotFaceCanvas(
    emotion: RobotEmotion,
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    audioLevel: Float = 0f,
    spokenWord: String = "",
    mouthOpening: Float = 0f
) {
    val primaryColor by animateColorAsState(
        targetValue = emotion.primaryColor,
        animationSpec = tween(600),
        label = "primaryColor"
    )
    val secondaryColor by animateColorAsState(
        targetValue = emotion.secondaryColor,
        animationSpec = tween(600),
        label = "secondaryColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "faceLoop")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val thinkingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingRotation"
    )

    // Speech Flap phase oscillation
    val speechTransition = rememberInfiniteTransition(label = "speechFlap")
    val speechPhase by speechTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(130, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speechPhase"
    )

    // Calculate target opening
    val targetMouthOpen = if (isSpeaking) {
        val baseOpening = if (mouthOpening > 0f) mouthOpening else 0.6f
        (baseOpening * (0.35f + 0.65f * speechPhase)).coerceIn(0.15f, 1.0f)
    } else {
        0.0f
    }

    val animatedMouthOpen by animateFloatAsState(
        targetValue = targetMouthOpen,
        animationSpec = tween(75, easing = FastOutSlowInEasing),
        label = "animatedMouthOpen"
    )

    // Periodic Eye Blinking logic
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val nextBlinkDelay = Random.nextLong(2500, 5000)
            delay(nextBlinkDelay)
            isBlinking = true
            delay(120)
            isBlinking = false
        }
    }

    val eyeBlinkScale by animateFloatAsState(
        targetValue = if (isBlinking) 0.05f else 1f,
        animationSpec = tween(110),
        label = "eyeBlinkScale"
    )

    val userDrawnStrokes = remember { mutableStateListOf<DrawnStroke>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Render Vector Drawable Resource with Blinking Animation
        Image(
            painter = painterResource(id = emotion.drawableRes),
            contentDescription = emotion.rawName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleY = eyeBlinkScale
                },
            contentScale = ContentScale.Fit
        )

        // Overlay Canvas for Thinking Ring, Aura, Mouth, and Interactive Mouse Drawing
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(primaryColor) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val newPath = Path().apply { moveTo(offset.x, offset.y) }
                            currentPath = newPath
                            userDrawnStrokes.add(DrawnStroke(newPath, primaryColor))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPath?.lineTo(change.position.x, change.position.y)
                        },
                        onDragEnd = {
                            currentPath = null
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f
            val scale = minOf(width / 800f, height / 480f)

            // Background Aura when listening or thinking
            if (isListening || isThinking) {
                drawAura(
                    centerX = centerX,
                    centerY = centerY,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    isListening = isListening,
                    isThinking = isThinking,
                    audioLevel = audioLevel,
                    pulseScale = pulseScale
                )
            }

            // Thinking Ring
            if (isThinking) {
                drawThinkingRing(
                    centerX = centerX,
                    centerY = centerY,
                    radius = width.coerceAtMost(height) * 0.42f,
                    color = primaryColor,
                    rotation = thinkingRotation
                )
            }

            // Dynamic Robot Mouth
            drawRobotMouth(
                centerX = centerX,
                centerY = centerY,
                scale = scale,
                emotion = emotion,
                spokenWord = spokenWord,
                mouthOpenAmount = animatedMouthOpen,
                primaryColor = primaryColor,
                speechPhase = speechPhase
            )

            // Interactive User Mouse Strokes
            for (stroke in userDrawnStrokes) {
                drawPath(
                    path = stroke.path,
                    color = stroke.color.copy(alpha = 0.35f),
                    style = Stroke(width = 22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = stroke.path,
                    color = stroke.color,
                    style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

data class DrawnStroke(
    val path: Path,
    val color: Color
)

private fun DrawScope.drawRobotMouth(
    centerX: Float,
    centerY: Float,
    scale: Float,
    emotion: RobotEmotion,
    spokenWord: String,
    mouthOpenAmount: Float,
    primaryColor: Color,
    speechPhase: Float
) {
    val mouthCenterX = centerX
    val mouthCenterY = centerY + 48f * scale

    val wordFlex = if (spokenWord.isNotEmpty()) (spokenWord.length * 1.8f * scale).coerceAtMost(18f * scale) else 0f
    val baseWidth = 100f * scale + wordFlex
    val baseStrokeWidth = 16f * scale

    if (mouthOpenAmount <= 0.02f) {
        val mouthPath = Path()
        when (emotion) {
            RobotEmotion.HAPPY -> {
                mouthPath.moveTo(mouthCenterX - baseWidth / 2f, mouthCenterY - 12f * scale)
                mouthPath.quadraticTo(
                    mouthCenterX, mouthCenterY + 28f * scale,
                    mouthCenterX + baseWidth / 2f, mouthCenterY - 12f * scale
                )
            }
            RobotEmotion.SAD -> {
                mouthPath.moveTo(mouthCenterX - baseWidth / 2f, mouthCenterY + 18f * scale)
                mouthPath.quadraticTo(
                    mouthCenterX, mouthCenterY - 22f * scale,
                    mouthCenterX + baseWidth / 2f, mouthCenterY + 18f * scale
                )
            }
            RobotEmotion.ANGRY -> {
                mouthPath.moveTo(mouthCenterX - baseWidth / 2f, mouthCenterY - 5f * scale)
                mouthPath.lineTo(mouthCenterX, mouthCenterY + 15f * scale)
                mouthPath.lineTo(mouthCenterX + baseWidth / 2f, mouthCenterY - 5f * scale)
            }
            RobotEmotion.CURIOUS -> {
                mouthPath.moveTo(mouthCenterX - baseWidth * 0.45f, mouthCenterY + 8f * scale)
                mouthPath.quadraticTo(
                    mouthCenterX, mouthCenterY - 12f * scale,
                    mouthCenterX + baseWidth * 0.5f, mouthCenterY - 6f * scale
                )
            }
            RobotEmotion.NEUTRAL -> {
                mouthPath.moveTo(mouthCenterX - baseWidth / 2f, mouthCenterY)
                mouthPath.lineTo(mouthCenterX + baseWidth / 2f, mouthCenterY)
            }
        }

        drawPath(
            path = mouthPath,
            color = primaryColor.copy(alpha = 0.3f),
            style = Stroke(width = baseStrokeWidth + 8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = mouthPath,
            color = primaryColor,
            style = Stroke(width = baseStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    } else {
        val speakWidth = baseWidth + 15f * scale * (1f - mouthOpenAmount * 0.4f)
        val speakHeight = 12f * scale + 48f * scale * mouthOpenAmount

        val halfW = speakWidth / 2f
        val halfH = speakHeight / 2f

        val topY = mouthCenterY - halfH
        val bottomY = mouthCenterY + halfH

        val auraRadius = speakWidth * 0.8f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor.copy(alpha = 0.35f * mouthOpenAmount), Color.Transparent),
                center = Offset(mouthCenterX, mouthCenterY),
                radius = auraRadius
            ),
            center = Offset(mouthCenterX, mouthCenterY),
            radius = auraRadius
        )

        val cavityPath = Path().apply {
            moveTo(mouthCenterX - halfW, mouthCenterY)
            quadraticTo(
                mouthCenterX, topY - (when (emotion) {
                    RobotEmotion.SAD -> 8f * scale
                    RobotEmotion.ANGRY -> 5f * scale
                    else -> 0f
                }),
                mouthCenterX + halfW, mouthCenterY
            )
            quadraticTo(
                mouthCenterX, bottomY + (when (emotion) {
                    RobotEmotion.HAPPY -> 8f * scale
                    else -> 0f
                }),
                mouthCenterX - halfW, mouthCenterY
            )
            close()
        }

        drawPath(
            path = cavityPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.45f * mouthOpenAmount),
                    Color(0xFF090D14)
                ),
                startY = topY,
                endY = bottomY
            )
        )

        if (speakHeight > 18f * scale) {
            val barCount = 3
            val barSpacing = speakWidth / (barCount + 1)
            for (i in 1..barCount) {
                val barX = (mouthCenterX - halfW) + barSpacing * i
                val barPulse = if (i % 2 == 1) speechPhase else (1.1f - speechPhase)
                val barH = (speakHeight * 0.55f) * barPulse
                drawLine(
                    color = primaryColor.copy(alpha = 0.85f),
                    start = Offset(barX, mouthCenterY - barH / 2f),
                    end = Offset(barX, mouthCenterY + barH / 2f),
                    strokeWidth = 6f * scale,
                    cap = StrokeCap.Round
                )
            }
        }

        drawPath(
            path = cavityPath,
            color = primaryColor.copy(alpha = 0.4f),
            style = Stroke(width = 16f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = cavityPath,
            color = primaryColor,
            style = Stroke(width = 10f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}


private fun DrawScope.drawAura(
    centerX: Float,
    centerY: Float,
    primaryColor: Color,
    secondaryColor: Color,
    isListening: Boolean,
    isThinking: Boolean,
    audioLevel: Float,
    pulseScale: Float
) {
    val auraRadius = (size.width.coerceAtMost(size.height) * 0.48f) * pulseScale *
            (if (isListening) 1f + audioLevel * 0.15f else 1f)

    val auraColor = when {
        isListening -> Color(0xFF00E5FF).copy(alpha = 0.25f + audioLevel * 0.25f)
        isThinking -> primaryColor.copy(alpha = 0.3f)
        else -> secondaryColor.copy(alpha = 0.12f)
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(auraColor, Color.Transparent),
            center = Offset(centerX, centerY),
            radius = auraRadius
        ),
        center = Offset(centerX, centerY),
        radius = auraRadius
    )
}

private fun DrawScope.drawThinkingRing(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color,
    rotation: Float
) {
    rotate(rotation, Offset(centerX, centerY)) {
        drawCircle(
            color = color.copy(alpha = 0.5f),
            center = Offset(centerX, centerY),
            radius = radius,
            style = Stroke(
                width = 6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f, 10f, 20f), 0f)
            )
        )
    }
}

@Preview(name = "Happy", showBackground = true, backgroundColor = 0xFF070A10)
@Composable
private fun RobotFaceCanvasHappyPreview() {
    SantiagoFaceTheme {
        RobotFaceCanvas(
            emotion = RobotEmotion.HAPPY,
            isListening = false,
            isThinking = false,
            isSpeaking = false
        )
    }
}

@Preview(name = "Sad", showBackground = true, backgroundColor = 0xFF070A10)
@Composable
private fun RobotFaceCanvasSadPreview() {
    SantiagoFaceTheme {
        RobotFaceCanvas(
            emotion = RobotEmotion.SAD,
            isListening = false,
            isThinking = false,
            isSpeaking = false
        )
    }
}

@Preview(name = "Angry", showBackground = true, backgroundColor = 0xFF070A10)
@Composable
private fun RobotFaceCanvasAngryPreview() {
    SantiagoFaceTheme {
        RobotFaceCanvas(
            emotion = RobotEmotion.ANGRY,
            isListening = false,
            isThinking = false,
            isSpeaking = false
        )
    }
}

@Preview(name = "Curious", showBackground = true, backgroundColor = 0xFF070A10)
@Composable
private fun RobotFaceCanvasCuriousPreview() {
    SantiagoFaceTheme {
        RobotFaceCanvas(
            emotion = RobotEmotion.CURIOUS,
            isListening = false,
            isThinking = true,
            isSpeaking = false
        )
    }
}

@Preview(name = "Neutral", showBackground = true, backgroundColor = 0xFF070A10)
@Composable
private fun RobotFaceCanvasNeutralPreview() {
    SantiagoFaceTheme {
        RobotFaceCanvas(
            emotion = RobotEmotion.NEUTRAL,
            isListening = false,
            isThinking = false,
            isSpeaking = false
        )
    }
}
