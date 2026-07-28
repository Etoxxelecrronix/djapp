package com.djapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Secondary,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Secondary,
    secondary = SecondaryLight,
    onSecondary = OnSurface,
    secondaryContainer = Secondary,
    onSecondaryContainer = OnSurface,
    tertiary = BpmBadge,
    onTertiary = Secondary,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    onError = OnSurface,
    outline = OnSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryDark,
    onPrimary = Surface,
    primaryContainer = Primary,
    onPrimaryContainer = Surface,
    secondary = SecondaryLight,
    onSecondary = Surface,
    secondaryContainer = Secondary,
    onSecondaryContainer = Surface,
    tertiary = BpmBadge,
    onTertiary = Surface,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    onError = Surface,
    outline = OnSurfaceVariant,
)

@Composable
fun DJAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
