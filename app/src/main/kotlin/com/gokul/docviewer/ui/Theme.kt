package com.gokul.docviewer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6B64),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBECEB),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A635F),
    surface = Color(0xFFFBFDFC),
    onSurface = Color(0xFF151B20),
    surfaceVariant = Color(0xFFDCE5E3),
    onSurfaceVariant = Color(0xFF3D4A52),
    error = Color(0xFF93372D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FB8AD),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF17322F),
    onPrimaryContainer = Color(0xFF9CF2E5),
    secondary = Color(0xFFB1CCC7),
    surface = Color(0xFF101518),
    onSurface = Color(0xFFE2E8EA),
    surfaceVariant = Color(0xFF2C373E),
    onSurfaceVariant = Color(0xFFB3C0C6),
    error = Color(0xFFDD8D80),
)

@Composable
fun DocViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
