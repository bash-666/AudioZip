package com.compresoraudio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00ABA9)
private val Cobalt = Color(0xFF0050EF)
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)

private val DarkColors = darkColorScheme(
    primary = Teal,
    secondary = Cobalt,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFE8EEF4),
    onSurface = Color(0xFFE8EEF4),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00706E),
    secondary = Cobalt,
    background = Color(0xFFF2F6FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF10202E),
    onSurface = Color(0xFF10202E),
)

@Composable
fun CompresorAudioTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
