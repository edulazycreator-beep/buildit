package com.buildit.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buildit.app.ui.theme.Palette.Blue10
import com.buildit.app.ui.theme.Palette.Blue20
import com.buildit.app.ui.theme.Palette.Blue30
import com.buildit.app.ui.theme.Palette.Blue40
import com.buildit.app.ui.theme.Palette.Blue80
import com.buildit.app.ui.theme.Palette.Blue90
import com.buildit.app.ui.theme.Palette.Blue95
import com.buildit.app.data.ThemeMode

/** Brand palette: a clear blue primary with a warm block-orange accent. */
object Palette {
    val Blue10 = Color(0xFF001A41)
    val Blue20 = Color(0xFF002E69)
    val Blue30 = Color(0xFF004494)
    val Blue40 = Color(0xFF1A5CC4)
    val Blue80 = Color(0xFFADC6FF)
    val Blue90 = Color(0xFFD8E2FF)
    val Blue95 = Color(0xFFEDF0FF)
    val Orange40 = Color(0xFF9A4600)
    val Orange80 = Color(0xFFFFB68A)
    val Orange90 = Color(0xFFFFDBC8)
    val Orange10 = Color(0xFF321200)
}

private val LightColors = lightColorScheme(
    primary = Blue40, onPrimary = Color.White, primaryContainer = Blue90, onPrimaryContainer = Blue10,
    secondary = Color(0xFF565E71), onSecondary = Color.White, secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Palette.Orange40, onTertiary = Color.White, tertiaryContainer = Palette.Orange90, onTertiaryContainer = Palette.Orange10,
    background = Color(0xFFFAF9FD), surface = Color(0xFFFAF9FD), surfaceContainerLow = Blue95.copy(alpha = 0.6f),
    surfaceVariant = Color(0xFFE1E2EC), onSurfaceVariant = Color(0xFF44474F), outline = Color(0xFF74777F),
)

private val DarkColors = darkColorScheme(
    primary = Blue80, onPrimary = Blue20, primaryContainer = Blue30, onPrimaryContainer = Blue90,
    secondary = Color(0xFFBEC6DC), onSecondary = Color(0xFF283141), secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Palette.Orange80, onTertiary = Color(0xFF532200), tertiaryContainer = Color(0xFF763300), onTertiaryContainer = Palette.Orange90,
    background = Color(0xFF121318), surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF44474F), onSurfaceVariant = Color(0xFFC4C6D0), outline = Color(0xFF8E9099),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun BuildItTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
