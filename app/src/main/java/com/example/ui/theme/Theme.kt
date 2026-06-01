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
  darkColorScheme(
    primary = PrimaryBlueDark,
    secondary = FoundationGray900,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkBorder,
    onPrimary = Color(0xFF0F172A), // Dark text on light blue buttons
    onSecondary = Color.White,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkTextSecondary
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BrandBlue, // Use BrandBlue instead of PrimaryBlue for Light Mode to make buttons pop
    secondary = FoundationWhite,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightBorder,
    onPrimary = Color.White,
    onSecondary = LightText,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightTextSecondary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Keep false for strict brand control
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
