package com.example.santiagoface.ui.face

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.santiagoface.R

enum class RobotEmotion(
    val rawName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val eyeColor: Color,
    @get:DrawableRes val drawableRes: Int
) {
    HAPPY(
        rawName = "happy",
        primaryColor = Color(0xFF00E5FF),   // Electric Cyan
        secondaryColor = Color(0xFFFFD600), // Vibrant Gold
        eyeColor = Color(0xFFBFFF00),       // Lime Green (matching eyes)
        drawableRes = R.drawable.happy
    ),
    SAD(
        rawName = "sad",
        primaryColor = Color(0xFF2979FF),   // Deep Blue
        secondaryColor = Color(0xFF7C4DFF), // Soft Purple
        eyeColor = Color(0xFF00E5FF),       // Cyan (matching eyes)
        drawableRes = R.drawable.sad
    ),
    ANGRY(
        rawName = "angry",
        primaryColor = Color(0xFFFF1744),   // Crimson Red
        secondaryColor = Color(0xFFFF6D00), // Flame Orange
        eyeColor = Color(0xFFD500F9),       // Neon Purple (matching eyes)
        drawableRes = R.drawable.angry
    ),
    CURIOUS(
        rawName = "curious",
        primaryColor = Color(0xFFE040FB),   // Bright Magenta
        secondaryColor = Color(0xFF00E676), // Neon Green
        eyeColor = Color(0xFFFF8000),       // Vibrant Orange (matching eyes)
        drawableRes = R.drawable.curious
    ),
    NEUTRAL(
        rawName = "neutral",
        primaryColor = Color(0xFF00E5FF),   // Cyan
        secondaryColor = Color(0xFF00E676), // Green Glow
        eyeColor = Color(0xFFAAAAAA),       // Silver / Grey (matching eyes)
        drawableRes = R.drawable.neutral
    );

    companion object {
        fun fromString(value: String?): RobotEmotion {
            val clean = value?.lowercase()?.trim() ?: ""
            return entries.find { it.rawName == clean } ?: NEUTRAL
        }
    }
}
