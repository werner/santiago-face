package com.example.santiagoface.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.santiagoface.ui.face.RobotEmotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    currentUrl: String,
    connectionStatus: Boolean?,
    autoListen: Boolean,
    spanishLocale: String,
    currentEmotion: RobotEmotion,
    onUrlSave: (String) -> Unit,
    onTestConnection: () -> Unit,
    onToggleAutoListen: (Boolean) -> Unit,
    onSelectSpanishLocale: (String) -> Unit,
    onSelectEmotion: (RobotEmotion) -> Unit,
    onResetHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var urlInput by remember { mutableStateOf(currentUrl) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF10141D),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Agent Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Agent Server Endpoint Section
            Text(
                text = "Agent Base URL (Rust Server)",
                fontSize = 14.sp,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("http://10.0.2.2:3000", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF2E3B52),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                trailingIcon = {
                    IconButton(onClick = { onUrlSave(urlInput) }) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Save URL",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Connection Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (connectionStatus) {
                        true -> Color(0xFF00E676)
                        false -> Color(0xFFFF1744)
                        null -> Color.Yellow
                    }
                    val statusText = when (connectionStatus) {
                        true -> "Connected to Agent"
                        false -> "Disconnected / Unreachable"
                        null -> "Testing Connection..."
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = statusText, fontSize = 13.sp, color = statusColor)
                }

                OutlinedButton(
                    onClick = onTestConnection,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                ) {
                    Text("Check", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Hands-Free Conversational Loop
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2333)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Re-Listen (Hands-Free)",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Automatically start listening after finishes speaking",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoListen,
                        onCheckedChange = onToggleAutoListen,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0xFF006064)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Spanish Region / Dialect Selection
            Text(
                text = "Región de Voz en Español",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(8.dp))

            val locales = listOf(
                "es-ES" to "España",
                "es-MX" to "México",
                "es-US" to "EE.UU.",
                "es-AR" to "Argentina",
                "es-CO" to "Colombia",
                "es-CL" to "Chile"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                locales.forEach { (tag, label) ->
                    val isSelected = tag == spanishLocale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF1B2333))
                            .clickable { onSelectSpanishLocale(tag) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Manual Emotion Tester
            Text(
                text = "Test Robot Emotions",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RobotEmotion.entries.forEach { emotion ->
                    val isSelected = emotion == currentEmotion
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) emotion.primaryColor else Color(0xFF1B2333))
                            .clickable { onSelectEmotion(emotion) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emotion.rawName.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Reset Memory Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onResetHistory,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF5252)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset History",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Agent History")
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
