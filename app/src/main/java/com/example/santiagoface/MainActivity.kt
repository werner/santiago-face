package com.example.santiagoface

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.santiagoface.ui.screens.RobotMainScreen
import com.example.santiagoface.ui.theme.SantiagoFaceTheme
import com.example.santiagoface.ui.viewmodel.RobotViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SantiagoFaceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val robotViewModel: RobotViewModel = viewModel()
                    RobotMainScreen(viewModel = robotViewModel)
                }
            }
        }
    }
}
