package com.example.santiagoface.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.santiagoface.data.VisionAnalysisDto
import com.example.santiagoface.ui.viewmodel.RobotViewModel

@Composable
fun CameraPreviewCard(
    viewModel: RobotViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        viewModel.startCamera(lifecycleOwner, previewView.surfaceProvider)
        onDispose {
            // Camera stopped when user closes or destroys
        }
    }

    Surface(
        modifier = modifier
            .width(180.dp)
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp)),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Live Camera Preview
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Control overlay buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .align(Alignment.TopEnd),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.toggleCameraLens(lifecycleOwner, previewView.surfaceProvider) },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Camera",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 3. Label
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "CAM (60° FOV)",
                    color = Color(0xFF00E5FF),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun VisionAnalysisOverlay(
    analysis: VisionAnalysisDto,
    modifier: Modifier = Modifier
) {
    val bbox = analysis.boundingBox

    Box(modifier = modifier.fillMaxSize()) {
        if (analysis.subjectVisible && bbox != null && bbox.width > 0f && bbox.height > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = bbox.x * size.width
                val top = bbox.y * size.height
                val rectWidth = bbox.width * size.width
                val rectHeight = bbox.height * size.height

                // Draw bounding box
                drawRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(left, top),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Target Info Box above bounding box
            val labelText = buildString {
                if (!analysis.recognizedKey.isNullOrBlank()) {
                    append(analysis.recognizedKey)
                } else if (analysis.detectedCategory != "none") {
                    append(analysis.detectedCategory.uppercase())
                } else {
                    append("TARGET")
                }
                analysis.estimatedDistanceCm?.let { dist ->
                    append(" | ${dist.toInt()} cm")
                }
                if (analysis.bearing != "none") {
                    append(" (${analysis.bearing})")
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = labelText,
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
