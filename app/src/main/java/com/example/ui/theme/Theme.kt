package com.example.ui.theme

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

@Composable
fun MyApplicationTheme(
  themeMode: String = "warrior",
  content: @Composable () -> Unit,
) {
  val colorScheme = if (themeMode == "focus") {
    lightColorScheme(
      primary = Color(0xFF4C6B53), // Sage Green Accent
      secondary = Color(0xFFC8815B), // Terracotta Study Accent
      tertiary = Color(0xFF7CA191), // Soft Olive/Jade
      background = Color(0xFFFCFAF5), // Warm White / Cream background
      surface = Color(0xFFFFFFFF), // White Cream Surface
      onPrimary = Color.White,
      onSecondary = Color.White,
      onTertiary = Color.White,
      onBackground = Color(0xFF2C2520), // Hardwood/Espresso Text
      onSurface = Color(0xFF332F2C),
      outline = Color(0xFFE6E1D8),
      surfaceVariant = Color(0xFFF5F2EA)
    )
  } else {
    // Premium Cyber/Dark Warrior Mode
    darkColorScheme(
      primary = Color(0xFFF1A80A), // Warrior Gold Accent
      secondary = Color(0xFF10B981), // Emerald Jade
      tertiary = Color(0xFF38BDF8), // Calm Sky Blue
      background = Color(0xFF090B11), // Slate Navy Galactic Obsidian
      surface = Color(0xFF111422), // Glass Navy surface card
      onPrimary = Color(0xFF05040A),
      onSecondary = Color.White,
      onTertiary = Color.White,
      onBackground = Color(0xFFF1F5F9), // Metallic Silver White
      onSurface = Color(0xFFE2E8F0),
      outline = Color(0xFF1E293B), // Dark slate stroke
      surfaceVariant = Color(0xFF1E2130)
    )
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
