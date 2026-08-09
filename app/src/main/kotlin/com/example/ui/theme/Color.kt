package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val FoundationBlack = Color(0xFF101828)
val FoundationWhite = Color(0xFFFFFFFF)
val FoundationGray50 = Color(0xFFF9FAFB)
val FoundationGray100 = Color(0xFFF3F4F6)
val FoundationGray200 = Color(0xFFE5E7EB)
val FoundationGray500 = Color(0xFF6B7280)
val FoundationGray900 = Color(0xFF111827)

val PrimaryBlue = Color(0xFF0F172A) // Slate 900 for Light Mode
val PrimaryBlueDark = Color(0xFF60A5FA) // Light Blue 400 for Dark Mode Accessibility
val BrandBlue = Color(0xFF2563EB) // Royal Blue
val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)

// Light Theme Colors
val LightBackground = FoundationGray50
val LightSurface = FoundationWhite
val LightText = FoundationGray900
val LightTextSecondary = FoundationGray500
val LightBorder = FoundationGray200

// Dark Theme Colors
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF111111)
val DarkText = FoundationGray50
val DarkTextSecondary = Color(0xFFA1A1AA)
val DarkBorder = Color(0xFF272A30)

// Compatibility Aliases
val PurplePrimary = PrimaryBlue
val PurpleSurface = LightBackground
val PurpleSurfaceVariant = LightSurface
val PurpleText = LightText
val PurpleSecondaryText = LightTextSecondary
val PurpleSecondary = FoundationGray100
val PurpleIconBg = FoundationGray200
val FabBg = PrimaryBlue
val FabText = FoundationWhite
val BorderColor = LightBorder

