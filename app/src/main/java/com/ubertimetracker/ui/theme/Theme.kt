package com.ubertimetracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Primary Brand Colors
val PurplePrimary = Color(0xFF6200EE)
val PurplePrimaryVariant = Color(0xFF3700B3)
val PurpleDark = Color(0xFFBB86FC)
val PurpleLight = Color(0xFFE8DEF8)

// Secondary Colors
val Teal200 = Color(0xFF03DAC5)
val Teal700 = Color(0xFF018786)

// Status Colors
val GreenOnline = Color(0xFF00C853)
val GreenStart = Color(0xFF4CAF50)
val RedStop = Color(0xFFF44336)
val OrangePause = Color(0xFFFF9800)
val YellowWarning = Color(0xFFFFC107)

// Background Colors
val BackgroundLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF121212)
val SurfaceLight = Color(0xFFF5F5F5)
val SurfaceDark = Color(0xFF1E1E1E)
val CardLight = Color(0xFFFFFFFF)
val CardDark = Color(0xFF2D2D2D)

// Text Colors
val TextPrimaryLight = Color(0xFF212121)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryLight = Color(0xFF757575)
val TextSecondaryDark = Color(0xFFB3B3B3)

// Weekend Colors
val WeekendHighlight = Color(0xFFFFF3E0)
val WeekendText = Color(0xFFFF9800)

// Console Colors
val ConsoleBackground = Color(0xFF1E1E2E)
val ConsoleText = Color(0xFFCDD6F4)
val ConsoleGreen = Color(0xFFA6E3A1)
val ConsoleYellow = Color(0xFFF9E2AF)
val ConsoleRed = Color(0xFFF38BA8)
val ConsoleCyan = Color(0xFF89DCEB)
val ConsolePurple = Color(0xFFCBA6F7)

// Gradient Colors
val GradientStartLight = Color(0xFFF3E5F5)
val GradientEndLight = Color(0xFFE1F5FE)
val GradientStartDark = Color(0xFF1A1A2E)
val GradientEndDark = Color(0xFF16213E)

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleLight,
    onPrimaryContainer = PurplePrimaryVariant,
    secondary = Teal200,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Teal700,
    tertiary = OrangePause,
    onTertiary = Color.Black,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = TextSecondaryLight,
    error = RedStop,
    onError = Color.White,
    outline = Color(0xFF79747E)
)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleDark,
    onPrimary = Color.Black,
    primaryContainer = PurplePrimaryVariant,
    onPrimaryContainer = PurpleLight,
    secondary = Teal200,
    onSecondary = Color.Black,
    secondaryContainer = Teal700,
    onSecondaryContainer = Teal200,
    tertiary = OrangePause,
    onTertiary = Color.Black,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = TextSecondaryDark,
    error = Color(0xFFCF6679),
    onError = Color.Black,
    outline = Color(0xFF938F99)
)

@Composable
fun UberTimeTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) {
                BackgroundDark.toArgb()
            } else {
                PurplePrimary.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Custom color extensions for easier access
object AppColors {
    @Composable
    fun statusRunning() = GreenOnline

    @Composable
    fun statusStopped() = RedStop

    @Composable
    fun statusPaused() = OrangePause

    @Composable
    fun weekend(isDark: Boolean) = if (isDark) OrangePause.copy(alpha = 0.3f) else WeekendHighlight

    @Composable
    fun weekendText() = WeekendText

    @Composable
    fun conflict() = YellowWarning

    @Composable
    fun console() = ConsoleBackground

    @Composable
    fun consoleText() = ConsoleText

    @Composable
    fun gradientStart(isDark: Boolean) = if (isDark) GradientStartDark else GradientStartLight

    @Composable
    fun gradientEnd(isDark: Boolean) = if (isDark) GradientEndDark else GradientEndLight
}
