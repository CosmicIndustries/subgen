package com.cosmicindustries.umbra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UmbraGreen = Color(0xFF6EE7B7)
private val UmbraGreenDark = Color(0xFF10B981)
private val UmbraBackground = Color(0xFF0B0F14)
private val UmbraSurface = Color(0xFF141B22)

private val DarkColors = darkColorScheme(
    primary = UmbraGreen,
    onPrimary = Color(0xFF00201A),
    secondary = UmbraGreenDark,
    background = UmbraBackground,
    surface = UmbraSurface,
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = UmbraGreenDark,
    secondary = UmbraGreen,
)

@Composable
fun UmbraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
