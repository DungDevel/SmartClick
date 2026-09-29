package com.example.smartclick.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class SmartClickColors(
    val bg: Color,
    val surf: Color,
    val surfEl: Color,
    val surfRa: Color,
    val bord: Color,
    val bordBr: Color,
    val acc: Color = Accent,
    val acGlow: Color = AccentGlow,
    val acDim: Color = AccentDim,
    val gradStart: Color = GradientStart,
    val gradMid: Color = GradientMid,
    val gradEnd: Color = GradientEnd,
    val orange: Color = Orange,
    val green: Color = Green,
    val red: Color = Red,
    val tx1: Color,
    val tx2: Color,
    val tx3: Color
) {
    val primaryGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(gradStart, gradMid, gradEnd)
        )
}

val DarkSmartClickColors = SmartClickColors(
    bg = DarkBackground,
    surf = DarkSurface,
    surfEl = DarkSurfaceElement,
    surfRa = DarkSurfaceRaised,
    bord = DarkBorder,
    bordBr = DarkBorderBright,
    tx1 = DarkText1,
    tx2 = DarkText2,
    tx3 = DarkText3
)

val LightSmartClickColors = SmartClickColors(
    bg = LightBackground,
    surf = LightSurface,
    surfEl = LightSurfaceElement,
    surfRa = LightSurfaceRaised,
    bord = LightBorder,
    bordBr = LightBorderBright,
    tx1 = LightText1,
    tx2 = LightText2,
    tx3 = LightText3
)

val LocalSmartClickColors = staticCompositionLocalOf { DarkSmartClickColors }

@Composable
fun SmartClickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val customColors = if (darkTheme) DarkSmartClickColors else LightSmartClickColors
    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = customColors.acc,
            background = customColors.bg,
            surface = customColors.surf,
            onPrimary = Color.White,
            onBackground = customColors.tx1,
            onSurface = customColors.tx1,
            surfaceVariant = customColors.surfEl,
            onSurfaceVariant = customColors.tx2,
            outline = customColors.bord
        )
    } else {
        lightColorScheme(
            primary = customColors.acc,
            background = customColors.bg,
            surface = customColors.surf,
            onPrimary = Color.White,
            onBackground = customColors.tx1,
            onSurface = customColors.tx1,
            surfaceVariant = customColors.surfEl,
            onSurfaceVariant = customColors.tx2,
            outline = customColors.bord
        )
    }

    CompositionLocalProvider(
        LocalSmartClickColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}

object SmartClickThemeTokens {
    val colors: SmartClickColors
        @Composable
        get() = LocalSmartClickColors.current
}
