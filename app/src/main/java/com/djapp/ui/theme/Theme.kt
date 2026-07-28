package com.djapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun DJAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
