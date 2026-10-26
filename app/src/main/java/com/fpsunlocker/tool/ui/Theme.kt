package com.fpsunlocker.tool.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// โทนเกมมิ่ง: น้ำเงินเข้ม + ฟ้านีออน + ม่วง + ส้มอำพัน
object Palette {
    val Cyan = Color(0xFF4FE3E8)
    val CyanDeep = Color(0xFF00848A)
    val Violet = Color(0xFFA78BFA)
    val VioletDeep = Color(0xFF6D4ED8)
    val Amber = Color(0xFFFFB547)
    val Good = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFF87171)
}

private val DarkColors = darkColorScheme(
    primary = Palette.Cyan,
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF0F3B47),
    onPrimaryContainer = Color(0xFFB8F6F8),
    secondary = Palette.Violet,
    onSecondary = Color(0xFF2A1466),
    secondaryContainer = Color(0xFF2E2560),
    onSecondaryContainer = Color(0xFFE3DAFF),
    tertiary = Palette.Amber,
    background = Color(0xFF0B0F1C),
    onBackground = Color(0xFFE4E8F5),
    surface = Color(0xFF0B0F1C),
    onSurface = Color(0xFFE4E8F5),
    surfaceVariant = Color(0xFF1C2338),
    onSurfaceVariant = Color(0xFFAEB6CC),
    surfaceContainerLowest = Color(0xFF080B15),
    surfaceContainerLow = Color(0xFF121829),
    surfaceContainer = Color(0xFF151C2F),
    surfaceContainerHigh = Color(0xFF1A2236),
    surfaceContainerHighest = Color(0xFF212A40),
    outline = Color(0xFF3A4560),
    outlineVariant = Color(0xFF283149),
)

private val LightColors = lightColorScheme(
    primary = Palette.CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F4F5),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Palette.VioletDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF22005D),
    tertiary = Color(0xFFB26A00),
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFF5F7FC),
    surfaceVariant = Color(0xFFE3E7F2),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F3FA),
    surfaceContainerHigh = Color(0xFFEAEEF7),
    surfaceContainerHighest = Color(0xFFE3E8F3),
)

@Composable
fun FpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
