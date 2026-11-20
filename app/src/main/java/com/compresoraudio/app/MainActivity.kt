package com.compresoraudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.compresoraudio.app.ui.CompressorScreen
import com.compresoraudio.app.ui.theme.CompresorAudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompresorAudioTheme {
                CompressorScreen()
            }
        }
    }
}
