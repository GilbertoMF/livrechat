package com.livrechat.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =========================================================================
// ESQUEMAS OFICIAIS DO MATERIAL 3 DESIGN KIT
// =========================================================================

val M3LightColorScheme = lightColorScheme(
    primary = M3PurplePrimary,
    onPrimary = M3PurpleOnPrimary,
    primaryContainer = M3PurplePrimaryContainer,
    onPrimaryContainer = M3PurpleOnPrimaryContainer,
    inversePrimary = M3PurplePrimaryDark,

    secondary = M3PurpleSecondary,
    onSecondary = M3PurpleOnSecondary,
    secondaryContainer = M3PurpleSecondaryContainer,
    onSecondaryContainer = M3PurpleOnSecondaryContainer,

    tertiary = M3PurpleTertiary,
    onTertiary = M3PurpleOnTertiary,
    tertiaryContainer = M3PurpleTertiaryContainer,
    onTertiaryContainer = M3PurpleOnTertiaryContainer,

    error = M3PurpleError,
    onError = M3PurpleOnError,
    errorContainer = M3PurpleErrorContainer,
    onErrorContainer = M3PurpleOnErrorContainer,

    background = M3PurpleBackground,
    onBackground = M3PurpleOnBackground,
    surface = M3PurpleSurface,
    onSurface = M3PurpleOnSurface,
    surfaceVariant = M3PurpleSurfaceVariant,
    onSurfaceVariant = M3PurpleOnSurfaceVariant,

    outline = M3PurpleOutline,
    outlineVariant = M3PurpleOutlineVariant,
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),

    surfaceContainerLowest = M3PurpleSurfaceContainerLowest,
    surfaceContainerLow = M3PurpleSurfaceContainerLow,
    surfaceContainer = M3PurpleSurfaceContainer,
    surfaceContainerHigh = M3PurpleSurfaceContainerHigh,
    surfaceContainerHighest = M3PurpleSurfaceContainerHighest
)

val M3DarkColorScheme = darkColorScheme(
    primary = M3PurplePrimaryDark,
    onPrimary = M3PurpleOnPrimaryDark,
    primaryContainer = M3PurplePrimaryContainerDark,
    onPrimaryContainer = M3PurpleOnPrimaryContainerDark,
    inversePrimary = M3PurplePrimary,

    secondary = M3PurpleSecondaryDark,
    onSecondary = M3PurpleOnSecondaryDark,
    secondaryContainer = M3PurpleSecondaryContainerDark,
    onSecondaryContainer = M3PurpleOnSecondaryContainerDark,

    tertiary = M3PurpleTertiaryDark,
    onTertiary = M3PurpleOnTertiaryDark,
    tertiaryContainer = M3PurpleTertiaryContainerDark,
    onTertiaryContainer = M3PurpleOnTertiaryContainerDark,

    error = M3PurpleErrorDark,
    onError = M3PurpleOnErrorDark,
    errorContainer = M3PurpleErrorContainerDark,
    onErrorContainer = M3PurpleOnErrorContainerDark,

    background = M3PurpleBackgroundDark,
    onBackground = M3PurpleOnBackgroundDark,
    surface = M3PurpleSurfaceDark,
    onSurface = M3PurpleOnSurfaceDark,
    surfaceVariant = M3PurpleSurfaceVariantDark,
    onSurfaceVariant = M3PurpleOnSurfaceVariantDark,

    outline = M3PurpleOutlineDark,
    outlineVariant = M3PurpleOutlineVariantDark,
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF313033),

    surfaceContainerLowest = M3PurpleSurfaceContainerLowestDark,
    surfaceContainerLow = M3PurpleSurfaceContainerLowDark,
    surfaceContainer = M3PurpleSurfaceContainerDark,
    surfaceContainerHigh = M3PurpleSurfaceContainerHighDark,
    surfaceContainerHighest = M3PurpleSurfaceContainerHighestDark
)

// =========================================================================
// CONSTRUTOR DE ESQUEMAS MATERIAL 3 PARA DEMAIS PALETAS
// =========================================================================

fun buildM3ColorScheme(
    primary: Color,
    primaryDark: Color,
    accent: Color,
    darkTheme: Boolean,
    isOLED: Boolean = false
): ColorScheme {
    return if (darkTheme) {
        val bg = if (isOLED) OLEDBackground else DarkBackground
        val surf = if (isOLED) OLEDSurface else DarkSurface
        val surfVar = if (isOLED) OLEDSurfaceVariant else DarkSurfaceVariant
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryDark,
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = Color.Black,
            secondaryContainer = accent.copy(alpha = 0.25f),
            onSecondaryContainer = Color.White,
            tertiary = accent,
            onTertiary = Color.Black,
            background = bg,
            onBackground = Color.White,
            surface = surf,
            onSurface = Color.White,
            surfaceVariant = surfVar,
            onSurfaceVariant = Color(0xFFCBD5E1),
            outline = Color(0xFF64748B),
            outlineVariant = Color(0xFF334155),
            error = DangerRed,
            onError = Color.White,
            surfaceContainerLowest = if (isOLED) Color.Black else Color(0xFF070C1B),
            surfaceContainerLow = bg,
            surfaceContainer = surf,
            surfaceContainerHigh = surfVar,
            surfaceContainerHighest = surfVar.copy(alpha = 0.9f)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.15f),
            onPrimaryContainer = primaryDark,
            secondary = accent,
            onSecondary = Color.White,
            secondaryContainer = accent.copy(alpha = 0.15f),
            onSecondaryContainer = primaryDark,
            tertiary = primaryDark,
            onTertiary = Color.White,
            background = LightBackground,
            onBackground = Color(0xFF0F172A),
            surface = LightSurface,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFF94A3B8),
            outlineVariant = Color(0xFFE2E8F0),
            error = DangerRed,
            onError = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF8FAFC),
            surfaceContainer = Color(0xFFF1F5F9),
            surfaceContainerHigh = Color(0xFFE2E8F0),
            surfaceContainerHighest = Color(0xFFCBD5E1)
        )
    }
}

fun getPaletteColors(palette: String): Triple<Color, Color, Color> {
    return when (palette) {
        "m3_kit", "m3", "material3" -> Triple(M3PurplePrimary, M3PurplePrimaryContainerDark, M3PurpleTertiary)
        "emerald" -> Triple(EmeraldPrimary, EmeraldDark, EmeraldAccent)
        "purple" -> Triple(PurplePrimary, PurpleDark, PurpleAccent)
        "amber" -> Triple(AmberPrimary, AmberDark, AmberAccent)
        "ruby" -> Triple(RubyPrimary, RubyDark, RubyAccent)
        "midnight" -> Triple(MidnightPrimary, MidnightDark, MidnightAccent)
        else -> Triple(M3PurplePrimary, M3PurplePrimaryContainerDark, M3PurpleTertiary)
    }
}

@Composable
fun LivreChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: String = "m3_kit",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isOLED = palette == "midnight" && darkTheme

    // Seleção de ColorScheme conforme Material 3 Design Kit / Dynamic Color
    val colorScheme: ColorScheme = when {
        // Suporte oficial a Material You (Cores Dinâmicas do Android 12+)
        (palette == "dynamic" || palette == "material_you") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Paleta Oficial do Material 3 Design Kit (Google Baseline - Roxo / Lavanda)
        palette == "m3_kit" || palette == "m3" || palette == "material3" || palette == "default" -> {
            if (darkTheme) M3DarkColorScheme else M3LightColorScheme
        }
        // Demais Paletas adaptadas com a régua de tokens M3
        else -> {
            val (pri, priDark, acc) = getPaletteColors(palette)
            buildM3ColorScheme(
                primary = pri,
                primaryDark = priDark,
                accent = acc,
                darkTheme = darkTheme,
                isOLED = isOLED
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Barra de status translúcida / harmonizada com M3
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
