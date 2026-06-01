package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = PurplePrimary,
    secondary = PurpleSecondary,
    tertiary = FabBg,
    background = PurpleSurface,
    surface = PurpleSurface,
    surfaceVariant = PurpleSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = PurpleSecondaryText,
    onBackground = PurpleText,
    onSurface = PurpleText,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color so we enforce our theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
