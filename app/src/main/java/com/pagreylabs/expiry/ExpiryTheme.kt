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

private val ExpiryPrimary = Color(0xFF0F7A4A)
private val ExpiryBackground = Color(0xFFF7F8F6)
private val ExpiryInk = Color(0xFF17201B)
private val ExpirySurface = Color(0xFFFFFFFF)
private val ExpirySurfaceVariant = Color(0xFFE8EEE9)
private val ExpiryAmber = Color(0xFFE8A814)
private val ExpiryRed = Color(0xFFC83D36)

private val LightColors = lightColorScheme(
    primary = ExpiryPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFE5),
    onPrimaryContainer = Color(0xFF063B23),
    secondary = ExpiryInk,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E9E5),
    onSecondaryContainer = ExpiryInk,
    tertiary = ExpiryAmber,
    onTertiary = Color(0xFF3B2E00),
    tertiaryContainer = Color(0xFFFFE3A0),
    onTertiaryContainer = Color(0xFF332600),
    background = ExpiryBackground,
    onBackground = ExpiryInk,
    surface = ExpirySurface,
    onSurface = ExpiryInk,
    surfaceVariant = ExpirySurfaceVariant,
    onSurfaceVariant = Color(0xFF4D5A53),
    outline = Color(0xFF7B897F),
    error = ExpiryRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF59C98A),
    onPrimary = Color(0xFF003A21),
    primaryContainer = Color(0xFF075D36),
    onPrimaryContainer = Color(0xFFB7F2CF),
    secondary = Color(0xFFC8D2CB),
    onSecondary = Color(0xFF2D352F),
    secondaryContainer = Color(0xFF3A443D),
    onSecondaryContainer = Color(0xFFE4EDE7),
    tertiary = Color(0xFFFFCA55),
    onTertiary = Color(0xFF412F00),
    tertiaryContainer = Color(0xFF5F4600),
    onTertiaryContainer = Color(0xFFFFE5A8),
    background = ExpiryInk,
    onBackground = Color(0xFFE8EEE9),
    surface = Color(0xFF1F2A23),
    onSurface = Color(0xFFE8EEE9),
    surfaceVariant = Color(0xFF29362E),
    onSurfaceVariant = Color(0xFFC1CCC4),
    outline = Color(0xFF89978F),
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
fun ExpiryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
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
        window.statusBarColor = if (resolvedDark) ExpiryInk.toArgbCompat()
        else ExpiryBackground.toArgbCompat()
        window.navigationBarColor = if (resolvedDark) ExpiryInk.toArgbCompat()
        else ExpirySurface.toArgbCompat()

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !resolvedDark
        controller.isAppearanceLightNavigationBars = !resolvedDark
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
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
