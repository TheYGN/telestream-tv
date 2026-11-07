package com.telestream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.telestream.tv.ui.AppScreen
import com.telestream.tv.ui.Root
import com.telestream.tv.ui.TeleStreamTheme
import com.telestream.tv.ui.UpdateDialog
import com.telestream.tv.ui.UpdateOffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** The screens opened so far; Back closes the top one. */
    val stack = mutableStateListOf<AppScreen>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (stack.isEmpty()) stack += if (Prefs.isSetUp) AppScreen.Main else AppScreen.Setup(first = true)
        setContent {
            TeleStreamTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
                    Root(stack.last(), open = { stack += it }, replace = { stack.clear(); stack += it })
                    UpdateDialog()
                }
            }
        }
        lifecycleScope.launch { UpdateOffer.checkOnStart() }
    }
}
