package com.example.blindlabel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * High-contrast color scheme for light theme.
 * Optimized for WCAG AAA compliance and low-vision accessibility.
 */
private val HighContrastLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3D),

    secondary = ButtonSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color.Black,

    tertiary = SuccessGreen,
    onTertiary = Color.White,

    error = AllergenWarningRed,
    onError = Color.White,
    errorContainer = AllergenWarningBackground,
    onErrorContainer = AllergenWarningRed,

    background = BackgroundLight,
    onBackground = TextPrimaryLight,

    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = TextSecondaryLight,

    outline = BorderLight,
    outlineVariant = Color(0xFF666666)
)

/**
 * High-contrast color scheme for dark theme.
 * Designed for maximum readability in low-light conditions.
 */
private val HighContrastDarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004080),
    onPrimaryContainer = Color(0xFFD6E4FF),

    secondary = ButtonSecondaryDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF404040),
    onSecondaryContainer = Color.White,

    tertiary = SuccessGreenDark,
    onTertiary = Color.Black,

    error = AllergenWarningRedDark,
    onError = Color.Black,
    errorContainer = AllergenWarningBackgroundDark,
    onErrorContainer = AllergenWarningRedDark,

    background = BackgroundDark,
    onBackground = TextPrimaryDark,

    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = TextSecondaryDark,

    outline = BorderDark,
    outlineVariant = Color(0xFF999999)
)

/**
 * BlindLabel Theme with high-contrast accessibility focus.
 * Disables dynamic colors to ensure consistent high-contrast experience.
 */
@Composable
fun BlindLabelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = true,
    content: @Composable () -> Unit
) {
    // Always use high-contrast schemes for accessibility
    val colorScheme = if (darkTheme) {
        HighContrastDarkColorScheme
    } else {
        HighContrastLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}