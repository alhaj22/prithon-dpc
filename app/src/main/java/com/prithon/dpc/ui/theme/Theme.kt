package com.prithon.dpc.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PrithonLightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = PureWhite,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue700,
    secondary = Slate900,
    onSecondary = PureWhite,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = Green600,
    onTertiary = PureWhite,
    tertiaryContainer = Green50,
    onTertiaryContainer = Green600,
    error = Red600,
    onError = PureWhite,
    errorContainer = Red50,
    onErrorContainer = Red700,
    background = Slate50,
    onBackground = Slate900,
    surface = PureWhite,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate200,
    outlineVariant = Slate400
)

@Composable
fun PrithonDpcTheme(
    darkStatusBar: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = if (darkStatusBar) Red700.toArgb() else Slate50.toArgb()
                window.navigationBarColor = PureWhite.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkStatusBar
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = PrithonLightColorScheme,
        typography = PrithonTypography,
        content = content
    )
}
