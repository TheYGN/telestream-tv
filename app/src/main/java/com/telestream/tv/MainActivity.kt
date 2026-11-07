package com.telestream.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.telestream.tv.ui.IntroOverlay
import com.telestream.tv.ui.IntroState
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

    /** The opening animation: only on a fresh launch, and not when it is turned off in the settings. */
    private lateinit var intro: IntroState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (stack.isEmpty()) stack += if (Prefs.isSetUp) AppScreen.Main else AppScreen.Setup(first = true)
        // animations turned off in the system (accessibility / Developer options) also turns the intro off
        val animationsOff = runCatching { android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
        intro = IntroState(enabled = savedInstanceState == null && Prefs.introMode != 2 && !animationsOff)
        com.telestream.tv.ui.IntroGate.done = intro.done
        setContent {
            TeleStreamTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
                    Box(Modifier.fillMaxSize()) {
                        // the home screen is composed (and loads) from the start; it is hidden while the intro covers it,
                        // then eases in from slightly larger as the camera flies through the logo
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            if (!intro.done) {
                                val r = intro.reveal
                                alpha = if (r <= 0f) 0f else 1f
                                val s = 1.06f - .06f * (1f - (1f - r) * (1f - r) * (1f - r))
                                scaleX = s; scaleY = s
                            }
                        }) {
                            Root(stack.last(), open = { stack += it }, replace = { stack.clear(); stack += it })
                        }
                        if (!intro.done) IntroOverlay(intro) { }
                    }
                    if (intro.done) UpdateDialog()
                }
            }
        }
        lifecycleScope.launch { UpdateOffer.checkOnStart() }
    }

    /** Any remote button during the intro skips straight to its ending instead of reaching the home screen. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::intro.isInitialized && !intro.done) {
            if (event.action == KeyEvent.ACTION_DOWN) intro.skip()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
