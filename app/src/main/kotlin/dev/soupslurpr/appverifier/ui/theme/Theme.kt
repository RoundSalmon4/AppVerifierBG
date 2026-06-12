package dev.soupslurpr.appverifier.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = Gold80,
    tertiary = BlueGrey80,
    secondaryContainer = Color(0xFF4E3500),
    onSecondaryContainer = Color(0xFFFFF3E0),
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = Gold40,
    tertiary = BlueGrey40,
    secondaryContainer = Color(0xFFFFF3E0),
    onSecondaryContainer = Color(0xFF1A237E),
)

@Composable
fun AppVerifierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}