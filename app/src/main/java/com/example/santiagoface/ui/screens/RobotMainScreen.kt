package com.example.santiagoface.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.santiagoface.ui.face.RobotFaceCanvas
import com.example.santiagoface.ui.viewmodel.RobotViewModel

@Composable
fun RobotMainScreen(viewModel: RobotViewModel) {
    val context = LocalContext.current

    val emotion by viewModel.emotion.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val showTextInput by viewModel.showTextInput.collectAsState()
    val autoListen by viewModel.autoListen.collectAsState()
    val spanishLocale by viewModel.spanishLocale.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val agentBaseUrl by viewModel.agentBaseUrl.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val partialSpeechText by viewModel.partialSpeechText.collectAsState()
    val spokenWord by viewModel.spokenWord.collectAsState()
    val mouthOpening by viewModel.mouthOpening.collectAsState()

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Permission launcher for microphone and location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val isLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true || permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        
        if (isLocationGranted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
                    location?.let {
                        viewModel.updateLocation(it.latitude, it.longitude)
                    }
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        }
        
        if (isAudioGranted) {
            viewModel.startListening()
        }
    }

    fun handleMicClick() {
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (hasLoc) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
                    location?.let {
                        viewModel.updateLocation(it.latitude, it.longitude)
                    }
                }
            } catch (e: SecurityException) {}
        }
        
        if (hasAudio) {
            viewModel.toggleListening()
        } else {
            val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (!hasLoc) {
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLoc) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
                    location?.let {
                        viewModel.updateLocation(it.latitude, it.longitude)
                    }
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A10))
    ) {
        // 1. Robot Face Canvas
        RobotFaceCanvas(
            emotion = emotion,
            isListening = isListening,
            isThinking = isThinking,
            isSpeaking = isSpeaking,
            spokenWord = spokenWord,
            mouthOpening = mouthOpening,
            audioLevel = audioLevel,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Sci-Fi Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Robot Title & Connection Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionStatus) {
                                true -> Color(0xFF00E676)
                                false -> Color(0xFFFF1744)
                                null -> Color.Yellow
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "",
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = when (connectionStatus) {
                            true -> "ONLINE"
                            false -> "OFFLINE"
                            null -> "CHECKING..."
                        },
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Quick Actions
            Row {
                IconButton(onClick = { viewModel.setShowTextInput(true) }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Text Input",
                        tint = Color(0xFF00E5FF)
                    )
                }
                IconButton(onClick = { viewModel.setShowSettings(true) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        }

        // 3. Speech / Status Display Banner (Center Bottom overlay)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display User Speech / Partial Transcription
            AnimatedVisibility(
                visible = isListening && partialSpeechText.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141E2E).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "“$partialSpeechText”",
                        color = Color(0xFF00E5FF),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 4. Bottom Mic Control Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            // Pulse animation when listening
            val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "micPulseScale"
            )

            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.3f))
                )
            }

            // Mic FAB Button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        brush = when {
                            isListening -> Brush.radialGradient(listOf(Color(0xFF00E5FF), Color(0xFF00838F)))
                            isThinking -> Brush.radialGradient(listOf(Color(0xFFFFD600), Color(0xFFF57F17)))
                            isSpeaking -> Brush.radialGradient(listOf(Color(0xFF00E676), Color(0xFF1B5E20)))
                            else -> Brush.radialGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                        }
                    )
                    .border(
                        width = 2.dp,
                        color = if (isListening) Color(0xFF00E5FF) else Color(0xFF334155),
                        shape = CircleShape
                    )
                    .clickable { handleMicClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                    contentDescription = "Microphone",
                    tint = if (isListening) Color.Black else Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // 5. Settings Bottom Sheet
        if (showSettings) {
            SettingsBottomSheet(
                currentUrl = agentBaseUrl,
                connectionStatus = connectionStatus,
                autoListen = autoListen,
                spanishLocale = spanishLocale,
                currentEmotion = emotion,
                onUrlSave = { viewModel.updateAgentUrl(it) },
                onTestConnection = { viewModel.testConnection() },
                onToggleAutoListen = { viewModel.toggleAutoListen(it) },
                onSelectSpanishLocale = { viewModel.updateSpanishLocale(it) },
                onSelectEmotion = { viewModel.setEmotion(it) },
                onResetHistory = { viewModel.resetHistory() },
                onDismiss = { viewModel.setShowSettings(false) }
            )
        }

        // 6. Manual Text Input Dialog
        if (showTextInput) {
            TextInputDialog(
                onSendMessage = { viewModel.processUserMessage(it) },
                onDismiss = { viewModel.setShowTextInput(false) }
            )
        }
    }
}
