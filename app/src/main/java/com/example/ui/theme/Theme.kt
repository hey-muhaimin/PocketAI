package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PocketAiColorScheme = darkColorScheme(
    primary = PocketAccent,
    onPrimary = PocketText,
    primaryContainer = PocketSurface,
    onPrimaryContainer = PocketText,
    secondary = PocketSuccess,
    onSecondary = PocketBg,
    secondaryContainer = PocketSurface,
    onSecondaryContainer = PocketSuccess,
    tertiary = PocketWarning,
    onTertiary = PocketBg,
    background = PocketBg,
    onBackground = PocketText,
    surface = PocketSurface,
    onSurface = PocketText,
    surfaceVariant = PocketSurface,
    onSurfaceVariant = PocketMuted,
    outline = PocketBorder,
    error = PocketDanger,
    onError = PocketText
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = PocketAiColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PocketBg.toArgb()
            window.navigationBarColor = PocketBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
