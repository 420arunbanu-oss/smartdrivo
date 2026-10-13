package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Brand Colors - Blue Theme (Flat, Clean)
val BluePrimary = Color(0xFF6556F5)        // Clean vibrant Blue (#1E88E5)
val BlueDark = Color(0xFF4338CA)           // Darker Blue for active/accent
val BlueLight = Color(0xFF9188FA)          // Lighter Blue
val BlueContainer = Color(0xFFEFEAFF)      // Light Blue pill/capsule background (#E3F2FD)
val BlueBorder = Color(0xFFC9C1FF)         // Light Blue border

// Backgrounds & Surfaces (Light Gray Screen Background + Pure White Cards)
val LightBackground = Color(0xFFF6F5FD)    // Light Gray screen background (#F5F5F5)
val LightSurface = Color(0xFFFFFFFF)       // Crisp pure white surface (#FFFFFF)
val CardBackground = Color(0xFFFFFFFF)     // Pure White card (#FFFFFF)
val CardBorderDefault = Color(0xFFE3E1F2)  // Flat subtle border (#E2E8F0)
val CardBorderFocus = Color(0xFFAAA0FA)    // Subtle blue focus border

// Dark Typography for Light Backgrounds
val TextDarkPrimary = Color(0xFF1D2542)    // High contrast slate (#0F172A)
val TextDarkSecondary = Color(0xFF57627C)  // Medium contrast slate (#475569)
val TextDarkTertiary = Color(0xFF8993AD)   // Muted contrast slate (#94A3B8)

// Status Colors (Flat, clean)
val StatusActiveGreen = Color(0xFF10B981)
val StatusActiveGreenBg = Color(0xFFECFDF5)
val StatusActiveGreenBorder = Color(0xFFA7F3D0)

val StatusInactiveRed = Color(0xFFEF4444)
val StatusInactiveRedBg = Color(0xFFFEF2F2)
val StatusInactiveRedBorder = Color(0xFFFECACA)

val StatusWarningYellow = Color(0xFFF59E0B)
val StatusWarningYellowBg = Color(0xFFFFFBEB)
val StatusWarningYellowBorder = Color(0xFFFDE68A)

// Platform Colors
val PlatformRapido = Color(0xFFF59E0B)
val PlatformRapidoBg = Color(0xFFFFFBEB)
val PlatformUber = Color(0xFF1E293B)
val PlatformOla = Color(0xFF10B981)

// Backward-compatibility Aliases (pointing to Blue theme)
val OrangePrimary = BluePrimary
val OrangeDark = BlueDark
val OrangeLight = BlueLight
val OrangeContainer = BlueContainer
val OrangeBorder = BlueBorder

val BlueSecondary = BluePrimary

val AccentGreen = StatusActiveGreen
val AccentRed = StatusInactiveRed
val AccentYellow = StatusWarningYellow
val AccentBlue = BluePrimary
val AccentUber = PlatformUber
val AccentOla = PlatformOla

val DarkBackground = LightBackground
val DarkSurface = LightSurface
val DarkCard = CardBackground
val DarkCardBorder = CardBorderDefault

val TextPrimaryDark = TextDarkPrimary
val TextSecondaryDark = TextDarkSecondary
val TextTertiaryDark = TextDarkTertiary

val LightCard = CardBackground
val LightCardBorder = CardBorderDefault
val TextPrimaryLight = TextDarkPrimary
val TextSecondaryLight = TextDarkSecondary


