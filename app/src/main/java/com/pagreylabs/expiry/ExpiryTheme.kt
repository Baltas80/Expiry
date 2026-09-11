package com.pagreylabs.expiry

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Approved Expiry visual language: green as the primary brand, blue for secondary
// information, amber for upcoming items and red for expired items.
private val ExpiryGreen = Color(0xFF2E7D32)
private val ExpiryGreenLight = Color(0xFF81C784)
private val ExpiryBlue = Color(0xFF0288D1)
private val ExpiryAmber = Color(0xFFFBC02D)
private val ExpiryRed = Color(0xFFE53935)
private val ExpiryInk = Color(0xFF18211F)
private val ExpiryBackground = Color(0xFFF8F9FA)
private val ExpirySurface = Color(0xFFFFFFFF)
private val ExpirySurfaceVariant = Color(0xFFE8EEEB)
private val ExpiryDarkBackground = Color(0xFF121212)
private val ExpiryDarkSurface = Color(0xFF1B211F)
private val ExpiryDarkSurfaceVariant = Color(0xFF26302C)

private val LightColors = lightColorScheme(
    primary = ExpiryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEDC),
    onPrimaryContainer = Color(0xFF0C3B12),
    secondary = ExpiryBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7EEFF),
    onSecondaryContainer = Color(0xFF00344F),
    tertiary = ExpiryAmber,
    onTertiary = Color(0xFF3B2E00),
    tertiaryContainer = Color(0xFFFFE8A3),
    onTertiaryContainer = Color(0xFF332600),
    background = ExpiryBackground,
    onBackground = ExpiryInk,
    surface = ExpirySurface,
    onSurface = ExpiryInk,
    surfaceVariant = ExpirySurfaceVariant,
    onSurfaceVariant = Color(0xFF43504C),
    outline = Color(0xFF77847F),
    error = ExpiryRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF123B16),
    primaryContainer = Color(0xFF245B28),
    onPrimaryContainer = Color(0xFFB9F0B9),
    secondary = Color(0xFF64B5F6),
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF004D73),
    onSecondaryContainer = Color(0xFFB9E3FF),
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color(0xFF3B2E00),
    tertiaryContainer = Color(0xFF5A4700),
    onTertiaryContainer = Color(0xFFFFE8A3),
    background = ExpiryDarkBackground,
    onBackground = Color(0xFFE2E8E4),
    surface = ExpiryDarkSurface,
    onSurface = Color(0xFFE2E8E4),
    surfaceVariant = ExpiryDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC1CBC5),
    outline = Color(0xFF89958F),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F0000),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ExpiryTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

private val ExpiryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ExpiryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = context.getSharedPreferences("expiry_settings", Context.MODE_PRIVATE)
        .getString("theme_mode", "system")
    val resolvedDark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }

    val window = (context as? ComponentActivity)?.window
    if (window != null) {
        // Keep the existing Expiry colors while using WindowCompat for system-bar icon
        // appearance. Avoid deprecated statusBarColor/navigationBarColor setters.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !resolvedDark
        controller.isAppearanceLightNavigationBars = !resolvedDark
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    MaterialTheme(
        colorScheme = if (resolvedDark) DarkColors else LightColors,
        typography = ExpiryTypography,
        shapes = ExpiryShapes,
        content = content
    )
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt().coerceIn(0, 255),
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255)
)
