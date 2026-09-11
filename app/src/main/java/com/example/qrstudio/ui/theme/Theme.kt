package com.example.qrstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF0DE),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFFB65B39),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCE),
    onSecondaryContainer = Color(0xFF3E0A00),
    tertiary = Color(0xFF765B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF251A00),
    background = Color(0xFFF8F9F7),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF8F9F7),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81D4C2),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF9DF0DE),
    secondary = Color(0xFFFFB59C),
    onSecondary = Color(0xFF5E1A08),
    secondaryContainer = Color(0xFF7D2E18),
    onSecondaryContainer = Color(0xFFFFDBCE),
    tertiary = Color(0xFFE8C55D),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4500),
    onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
)

@Composable
fun QRStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
