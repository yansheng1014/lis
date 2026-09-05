package com.lis.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val LisDarkColors = ColorScheme(
    primary = Color(0xFFA6C8FF),
    primaryDim = Color(0xFF3D6B9E),
    primaryContainer = Color(0xFF284777),
    onPrimary = Color(0xFF00315F),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBCC7DC),
    secondaryDim = Color(0xFF545F70),
    secondaryContainer = Color(0xFF3A4659),
    onSecondary = Color(0xFF263141),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFD9B9E9),
    tertiaryDim = Color(0xFF6B5778),
    tertiaryContainer = Color(0xFF503D5F),
    onTertiary = Color(0xFF3D2748),
    onTertiaryContainer = Color(0xFFF3D9FF),
    surfaceContainerLow = Color(0xFF0A0B0E),
    surfaceContainer = Color(0xFF111318),
    surfaceContainerHigh = Color(0xFF1B1D22),
    onSurface = Color(0xFFE1E2E7),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF3F434A),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE1E2E7),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFBA1A1A),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun LisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LisDarkColors, content = content)
}