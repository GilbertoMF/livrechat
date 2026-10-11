package com.livrechat.app.ui.theme

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

fun getPaletteColors(palette: String): Triple<Color, Color, Color> {
    return when (palette) {
        "emerald" -> Triple(EmeraldPrimary, EmeraldDark, EmeraldAccent)
        "purple" -> Triple(PurplePrimary, PurpleDark, PurpleAccent)
        "amber" -> Triple(AmberPrimary, AmberDark, AmberAccent)
        "ruby" -> Triple(RubyPrimary, RubyDark, RubyAccent)
        "midnight" -> Triple(MidnightPrimary, MidnightDark, MidnightAccent)
        else -> Triple(BluePrimary, BlueDark, BlueAccent)
    }
}

@Composable
fun LivreChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: String = "default",
    content: @Composable () -> Unit
) {
    val (primary, dark, accent) = getPaletteColors(palette)
    val isOLED = palette == "midnight" && darkTheme

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = LightSurface,
            primaryContainer = dark,
            secondary = accent,
            background = if (isOLED) OLEDBackground else DarkBackground,
            surface = if (isOLED) OLEDSurface else DarkSurface,
            surfaceVariant = if (isOLED) OLEDSurfaceVariant else DarkSurfaceVariant,
            onBackground = LightSurface,
            onSurface = LightSurface
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = LightSurface,
            primaryContainer = primary.copy(alpha = 0.15f),
            secondary = accent,
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onBackground = DarkBackground,
            onSurface = DarkBackground
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
