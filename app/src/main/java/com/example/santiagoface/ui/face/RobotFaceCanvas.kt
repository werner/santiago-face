package com.example.santiagoface.ui.face

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.santiagoface.ui.theme.SantiagoFaceTheme

@Composable
fun RobotFaceCanvas(
    emotion: RobotEmotion,
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    audioLevel: Float = 0f
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

    val userDrawnStrokes = remember { mutableStateListOf<DrawnStroke>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Render Vector Drawable Resource for the selected emotion
        Image(
            painter = painterResource(id = emotion.drawableRes),
            contentDescription = emotion.rawName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Overlay Canvas for Thinking Ring, Aura, and Interactive Mouse Drawing
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
