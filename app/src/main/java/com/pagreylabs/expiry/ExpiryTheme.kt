package com.pagreylabs.expiry

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape

private val ExpiryGreen = Color(0xFF197A5B)
private val ExpiryGreenLight = Color(0xFFD5F1E5)
private val ExpiryTeal = Color(0xFF147D78)
private val ExpiryInk = Color(0xFF18211F)
private val ExpiryBackground = Color(0xFFF6F8F7)
private val ExpirySurface = Color(0xFFFFFFFF)
private val ExpiryDarkBackground = Color(0xFF101514)
private val ExpiryDarkSurface = Color(0xFF18201E)
private val ExpiryDarkSurfaceVariant = Color(0xFF24302C)

private val LightColors = lightColorScheme(
    primary = ExpiryGreen,
    onPrimary = Color.White,
    primaryContainer = ExpiryGreenLight,
    onPrimaryContainer = Color(0xFF00382A),
    secondary = ExpiryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1EFEC),
    onSecondaryContainer = Color(0xFF00201E),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    background = ExpiryBackground,
    onBackground = ExpiryInk,
    surface = ExpirySurface,
    onSurface = ExpiryInk,
    surfaceVariant = Color(0xFFE6ECE9),
    onSurfaceVariant = Color(0xFF43504C),
    outline = Color(0xFF7A8883),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D9B4),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00523C),
    onPrimaryContainer = Color(0xFF97F8CF),
    secondary = Color(0xFF72D7D0),
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF00504C),
    onSecondaryContainer = Color(0xFF91F2EA),
    tertiary = Color(0xFFFFBD57),
    onTertiary = Color(0xFF472A00),
    background = ExpiryDarkBackground,
    onBackground = Color(0xFFE0E7E3),
    surface = ExpiryDarkSurface,
    onSurface = Color(0xFFE0E7E3),
    surfaceVariant = ExpiryDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC0CAC5),
    outline = Color(0xFF89948F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
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
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ExpiryTypography,
        shapes = ExpiryShapes,
        content = content
    )
}
