package com.saper.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClassicColorScheme = lightColorScheme(
    primary = Color(0xFF1458BA),
    secondary = WindowInk,
    background = WindowPanel,
    surface = WindowPanel,
    surfaceContainerHigh = WindowPanel,
    onBackground = Color(0xFF202020),
    onSurface = Color(0xFF202020)
)

@Composable
fun SaperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ClassicColorScheme, typography = Typography, content = content)
}
