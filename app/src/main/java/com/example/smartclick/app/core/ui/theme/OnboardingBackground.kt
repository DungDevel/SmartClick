package com.example.smartclick.app.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun Modifier.onboardingBackground(): Modifier {
    val colors = SmartClickThemeTokens.colors
    return if (colors.bg == DarkBackground) {
        background(Brush.verticalGradient(listOf(Color(0xFF132135), Color(0xFF28428E))))
    } else {
        background(colors.bg)
    }
}
