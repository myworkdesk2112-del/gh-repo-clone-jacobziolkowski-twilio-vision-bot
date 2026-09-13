package com.giva.hiassist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EvenInspiredColors = lightColorScheme(
    primary = Color(0xFF232323),
    onPrimary = Color.White,
    secondary = Color(0xFF7B7B7B),
    background = Color(0xFFEEEEEE),
    surface = Color.White,
    surfaceVariant = Color(0xFFE4E4E4),
    onBackground = Color(0xFF232323),
    onSurface = Color(0xFF232323),
    onSurfaceVariant = Color(0xFF7B7B7B)
)

@Composable
fun HiAssistTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EvenInspiredColors, content = content)
}
