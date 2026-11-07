package com.telestream.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Bg = Color(0xFF0E0F14)
val Panel = Color(0xFF1A1D27)
val Accent = Color(0xFF3EA6FF)
val Muted = Color(0xFFA3A8B8)

@Composable
fun TeleStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.Black, background = Bg, surface = Panel,
            onSurface = Color.White, onBackground = Color.White,
        ),
        content = content,
    )
}
