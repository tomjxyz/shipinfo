package com.tomjxyz.shipinfo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3FF),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF0B4A6F),
    onPrimaryContainer = Color(0xFFC6E7FF),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF5C3F00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = Color(0xFF80E0B0),
    background = Color(0xFF0B1D33),
    onBackground = Color(0xFFE1E7EE),
    surface = Color(0xFF0B1D33),
    onSurface = Color(0xFFE1E7EE),
    surfaceVariant = Color(0xFF1F3550),
    onSurfaceVariant = Color(0xFFB9C7D6),
    surfaceContainer = Color(0xFF132A44),
    surfaceContainerHigh = Color(0xFF1A334F),
    surfaceContainerLow = Color(0xFF10253D),
    outline = Color(0xFF6C8196),
    outlineVariant = Color(0xFF34495F),
    error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0B5E8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6E7FF),
    onPrimaryContainer = Color(0xFF001E2F),
    secondary = Color(0xFFB36B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB3),
    onSecondaryContainer = Color(0xFF2A1800),
    tertiary = Color(0xFF1E7F55),
    background = Color(0xFFF5F8FB),
    surface = Color(0xFFF5F8FB),
    surfaceVariant = Color(0xFFDDE5EE),
    surfaceContainer = Color(0xFFE9EFF5),
    surfaceContainerHigh = Color(0xFFE2E9F0),
    surfaceContainerLow = Color(0xFFEFF3F8),
)

/** Series colours used consistently across charts. */
object ChartColors {
    val speed = Color(0xFF4FC3F7)
    val cog = Color(0xFF4FC3F7)
    val compass = Color(0xFFFFB74D)
    val roll = Color(0xFF81C784)
    val pitch = Color(0xFFBA68C8)
    val lateral = Color(0xFFFF8A65)
    val vertical = Color(0xFF90A4AE)
    val record = Color(0xFFFF5252)
    val port = Color(0xFFEF5350)
    val starboard = Color(0xFF66BB6A)
}

@Composable
fun ShipInfoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
