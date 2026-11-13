package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = KinIndigoLight,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = KinCoralSecondary,
    onSecondary = Color.White,
    tertiary = KinTealAccent,
    background = KinBackgroundDark,
    onBackground = Color(0xFFF1F5F9),
    surface = KinSurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = KinSurfaceVariantDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = KinSlateBorder
)

private val LightColorScheme = lightColorScheme(
    primary = KinIndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = KinCoralSecondary,
    onSecondary = Color.White,
    tertiary = KinTealAccent,
    background = KinBackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = KinSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = KinSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun PodAllyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent branding colors
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
