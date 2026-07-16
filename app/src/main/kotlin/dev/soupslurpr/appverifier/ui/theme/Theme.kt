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

private fun Color.lighten(factor: Float = 0.5f): Color {
    val red = red + (1f - red) * factor
    val green = green + (1f - green) * factor
    val blue = blue + (1f - blue) * factor
    return Color(red, green, blue, alpha)
}

private fun standardLightScheme(primaryColor: Int, secondaryColor: Int) = lightColorScheme(
    primary = Color(primaryColor),
    secondary = Color(secondaryColor),
    tertiary = BlueGrey40,
    secondaryContainer = Color(0xFFFFF3E0),
    onSecondaryContainer = Color(primaryColor),
)

private fun standardDarkScheme(primaryColor: Int, secondaryColor: Int) = darkColorScheme(
    primary = Color(primaryColor).lighten(0.4f),
    secondary = Color(secondaryColor),
    tertiary = BlueGrey80,
    secondaryContainer = Color(0xFF4E3500),
    onSecondaryContainer = Color(0xFFFFF3E0),
)

private fun amoledDarkScheme(primaryColor: Int, secondaryColor: Int) = darkColorScheme(
    primary = Color(primaryColor).lighten(0.4f),
    secondary = Color(secondaryColor),
    tertiary = BlueGrey80,
    secondaryContainer = Color(0xFF4E3500),
    onSecondaryContainer = Color(0xFFFFF3E0),
    surface = Color.Black,
    background = Color.Black,
)

@Composable
fun AppVerifierTheme(
    themeMode: String = "SYSTEM",
    useAmoledTheme: Boolean = false,
    colorSchemeMode: String = "STANDARD",
    primaryColor: Int = 0xFF1A237E.toInt(),
    secondaryColor: Int = 0xFFFFD54F.toInt(),
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
        darkTheme && useAmoledTheme -> amoledDarkScheme(primaryColor, secondaryColor)
        darkTheme -> standardDarkScheme(primaryColor, secondaryColor)
        else -> standardLightScheme(primaryColor, secondaryColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
