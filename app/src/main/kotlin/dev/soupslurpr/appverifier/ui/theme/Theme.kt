package dev.soupslurpr.appverifier.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = Gold80,
    tertiary = BlueGrey80,
    secondaryContainer = Color(0xFF4E3500),
    onSecondaryContainer = Color(0xFFFFF3E0),
)

private val DarkAmoledColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = Gold80,
    tertiary = BlueGrey80,
    secondaryContainer = Color(0xFF4E3500),
    onSecondaryContainer = Color(0xFFFFF3E0),
    surface = Color.Black,
    background = Color.Black,
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
    themeMode: String = "SYSTEM",
    useAmoledTheme: Boolean = false,
    colorSchemeMode: String = "STANDARD",
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()

    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemDarkTheme
    }

    val colorScheme = when {
        colorSchemeMode == "DYNAMIC_COLOR" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme && useAmoledTheme -> DarkAmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
