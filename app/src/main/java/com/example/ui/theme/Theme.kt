package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepNavyBackground,
    primaryContainer = DarkNavyCard,
    onPrimaryContainer = BrightCyan,
    secondary = BrightCyan,
    onSecondary = DeepNavyBackground,
    secondaryContainer = DarkNavySurface,
    onSecondaryContainer = GlowCyan,
    tertiary = GlowCyan,
    background = DeepNavyBackground,
    onBackground = TextCyanPrimary,
    surface = DarkNavySurface,
    onSurface = TextCyanPrimary,
    surfaceVariant = DarkNavyCard,
    onSurfaceVariant = TextCyanSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Force custom JARVIS glowing cyan palette
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
