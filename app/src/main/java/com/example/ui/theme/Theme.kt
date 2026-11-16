package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.example.domain.ThemeMode

private val DarkColorScheme =
  darkColorScheme(
    primary = MintPrimary,
    onPrimary = MintOnPrimary,
    primaryContainer = MintContainer,
    onPrimaryContainer = MintOnContainer,
    secondary = CoralSecondary,
    onSecondary = CoralOnSecondary,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = CoralOnContainer,
    tertiary = SkyTertiary,
    onTertiary = SkyOnTertiary,
    tertiaryContainer = SkyContainer,
    onTertiaryContainer = SkyOnContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianOutline,
    error = DangerRed,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = TerracottaSecondary,
    onSecondary = TerracottaOnSecondary,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = TerracottaOnContainer,
    tertiary = OceanTertiary,
    onTertiary = OceanOnTertiary,
    tertiaryContainer = OceanContainer,
    onTertiaryContainer = OceanOnContainer,
    background = CloudBg,
    onBackground = TextPrimaryLight,
    surface = CloudSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = CloudSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = CloudOutline,
    error = DangerRed,
  )

val StepmeterShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
  )

@Composable
fun StepmeterTheme(
  themeMode: ThemeMode = ThemeMode.DARK,
  content: @Composable () -> Unit,
) {
  val isDark =
    when (themeMode) {
      ThemeMode.DARK -> true
      ThemeMode.LIGHT -> false
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
  val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
  val metricPalette = if (isDark) DarkMetricPalette else LightMetricPalette

  CompositionLocalProvider(LocalMetricPalette provides metricPalette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = StepmeterTypography,
      shapes = StepmeterShapes,
      content = content,
    )
  }
}
