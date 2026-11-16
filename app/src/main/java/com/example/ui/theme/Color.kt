package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark Palette (Midnight Obsidian Athletic)
val ObsidianBg = Color(0xFF0B0F14)
val ObsidianSurface = Color(0xFF131A22)
val ObsidianSurfaceVariant = Color(0xFF1B2430)
val ObsidianSurfaceElevated = Color(0xFF232F3E)
val ObsidianOutline = Color(0xFF2E3D4F)

val MintPrimary = Color(0xFF2CE69B)
val MintOnPrimary = Color(0xFF042919)
val MintContainer = Color(0xFF0D3B28)
val MintOnContainer = Color(0xFF8FF7C8)

val CoralSecondary = Color(0xFFFF6B4A)
val CoralOnSecondary = Color(0xFF330D04)
val CoralContainer = Color(0xFF4B1B10)
val CoralOnContainer = Color(0xFFFFB4A1)

val SkyTertiary = Color(0xFF4DA6FF)
val SkyOnTertiary = Color(0xFF052442)
val SkyContainer = Color(0xFF103459)
val SkyOnContainer = Color(0xFFB5DAFF)

val TextPrimaryDark = Color(0xFFF3F6FA)
val TextSecondaryDark = Color(0xFF9AA8B8)

// Light Palette (Crisp Daylight Athletic)
val CloudBg = Color(0xFFF5F7FA)
val CloudSurface = Color(0xFFFFFFFF)
val CloudSurfaceVariant = Color(0xFFE9EFF6)
val CloudOutline = Color(0xFFD0DAE5)

val EmeraldPrimary = Color(0xFF00A86B)
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldContainer = Color(0xFFCFF7E4)
val EmeraldOnContainer = Color(0xFF003821)

val TerracottaSecondary = Color(0xFFE04E2E)
val TerracottaOnSecondary = Color(0xFFFFFFFF)
val TerracottaContainer = Color(0xFFFFDBD2)
val TerracottaOnContainer = Color(0xFF4A1205)

val OceanTertiary = Color(0xFF1B76D1)
val OceanOnTertiary = Color(0xFFFFFFFF)
val OceanContainer = Color(0xFFD5E9FF)
val OceanOnContainer = Color(0xFF002D57)

val TextPrimaryLight = Color(0xFF101820)
val TextSecondaryLight = Color(0xFF566575)

val GoldHighlight = Color(0xFFFFC857)
val DangerRed = Color(0xFFFF5C6C)

data class MetricPalette(
  val stepsAccent: Color,
  val caloriesAccent: Color,
  val distanceAccent: Color,
  val goldAccent: Color,
  val cardElevated: Color,
  val subtleBorder: Color,
)

val DarkMetricPalette =
  MetricPalette(
    stepsAccent = MintPrimary,
    caloriesAccent = CoralSecondary,
    distanceAccent = SkyTertiary,
    goldAccent = GoldHighlight,
    cardElevated = ObsidianSurfaceElevated,
    subtleBorder = ObsidianOutline,
  )

val LightMetricPalette =
  MetricPalette(
    stepsAccent = EmeraldPrimary,
    caloriesAccent = TerracottaSecondary,
    distanceAccent = OceanTertiary,
    goldAccent = Color(0xFFD99B00),
    cardElevated = CloudSurfaceVariant,
    subtleBorder = CloudOutline,
  )

val LocalMetricPalette = staticCompositionLocalOf { DarkMetricPalette }
