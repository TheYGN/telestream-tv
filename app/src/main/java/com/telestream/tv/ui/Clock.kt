package com.telestream.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The time of day as the TV shows it, 24 hours: "21:47". */
fun clockNow(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

/** The time of day, kept current: it changes on the minute, and is right again after the app was in the background. */
@Composable
fun clockText(): String {
    var text by remember { mutableStateOf(clockNow()) }
    LaunchedEffect(Resumed.tick) {
        while (true) {
            text = clockNow()
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }
    return text
}
