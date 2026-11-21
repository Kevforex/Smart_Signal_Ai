package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TradingColors(
    val bull: Color,
    val bullContainer: Color,
    val bear: Color,
    val bearContainer: Color,
    val pending: Color,
    val pendingContainer: Color,
    val ema50: Color,
    val ema200: Color,
    val border: Color,
    val cardElevated: Color,
    val mutedText: Color
)

val LocalTradingColors = staticCompositionLocalOf {
    TradingColors(
        bull = BullEmerald,
        bullContainer = BullEmeraldSoft,
        bear = BearCrimson,
        bearContainer = BearCrimsonSoft,
        pending = AmberPending,
        pendingContainer = AmberPendingSoft,
        ema50 = CyanPrimary,
        ema200 = VioletEma200,
        border = ObsidianBorder,
        cardElevated = ObsidianCardElevated,
        mutedText = TextLightMuted
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = ObsidianBg,
    primaryContainer = CyanSoft,
    onPrimaryContainer = CyanPrimary,
    secondary = BullEmerald,
    onSecondary = ObsidianBg,
    secondaryContainer = BullEmeraldSoft,
    onSecondaryContainer = BullEmerald,
    tertiary = VioletEma200,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextLightPrimary,
    surface = ObsidianSurface,
    onSurface = TextLightPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextLightSecondary,
    outline = ObsidianBorder,
    error = BearCrimson,
    onError = Color.White,
    errorContainer = BearCrimsonSoft,
    onErrorContainer = BearCrimson
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = TextDarkPrimary,
    surface = DaylightSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextDarkSecondary,
    outline = DaylightBorder,
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF9F1239)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun SmartSignalTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val tradingColors = if (darkTheme) {
        TradingColors(
            bull = BullEmerald,
            bullContainer = BullEmeraldSoft,
            bear = BearCrimson,
            bearContainer = BearCrimsonSoft,
            pending = AmberPending,
            pendingContainer = AmberPendingSoft,
            ema50 = CyanPrimary,
            ema200 = VioletEma200,
            border = ObsidianBorder,
            cardElevated = ObsidianCardElevated,
            mutedText = TextLightMuted
        )
    } else {
        TradingColors(
            bull = Color(0xFF059669),
            bullContainer = Color(0xFFD1FAE5),
            bear = Color(0xFFE11D48),
            bearContainer = Color(0xFFFFE4E6),
            pending = Color(0xFFD97706),
            pendingContainer = Color(0xFFFEF3C7),
            ema50 = Color(0xFF0284C7),
            ema200 = Color(0xFF7C3AED),
            border = DaylightBorder,
            cardElevated = DaylightSurface,
            mutedText = TextDarkMuted
        )
    }

    CompositionLocalProvider(LocalTradingColors provides tradingColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
