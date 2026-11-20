package com.supplicaste.onda.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = OndaAccent,
    onPrimary = OndaBackground,
    primaryContainer = OndaAccentDim,
    onPrimaryContainer = OndaOnSurface,
    secondary = OndaAccentDim,
    onSecondary = OndaBackground,
    background = OndaBackground,
    onBackground = OndaOnSurface,
    surface = OndaSurface,
    onSurface = OndaOnSurface,
    surfaceVariant = OndaSurfaceHigh,
    onSurfaceVariant = OndaMuted,
    outline = OndaMuted
)

private val LightColors = lightColorScheme(
    primary = OndaAccentDim,
    onPrimary = OndaOnSurface,
    primaryContainer = OndaAccent,
    onPrimaryContainer = OndaBackground,
    secondary = OndaAccentDim,
    background = OndaOnSurface,
    onBackground = OndaBackground,
    surface = Color(0xFFF7F7F7),
    onSurface = OndaBackground,
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFF777777)
)

@Composable
fun OndaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
