package com.telestream.tv.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.tv.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.telestream.tv.PlayRequest
import com.telestream.tv.PlayerActivity
import androidx.compose.ui.platform.LocalContext

sealed class AppScreen {
    data object Main : AppScreen()
    data class Setup(val first: Boolean) : AppScreen()
    data class MovieDetails(val key: String, val title: String) : AppScreen()
    data class ShowDetails(val key: String) : AppScreen()
    data class Browse(val kind: String, val genre: String?, val sort: String? = null, val title: String? = null) : AppScreen()
    data object Other : AppScreen()
    data class Upcoming(val key: String) : AppScreen()
}

enum class Tab(val label: String) { Home("בית"), Movies("סרטים"), Shows("סדרות"), Search("חיפוש"), Settings("הגדרות") }

/** What every screen gets: open another screen, or play something. */
class Nav(val open: (AppScreen) -> Unit, val replace: (AppScreen) -> Unit, val play: (PlayRequest) -> Unit)

@Composable
fun Root(screen: AppScreen, open: (AppScreen) -> Unit, replace: (AppScreen) -> Unit, alive: List<String> = emptyList()) {
    val context = LocalContext.current
    val nav = Nav(open, replace) { PlayerActivity.start(context, it) }
    val holder = rememberSaveableStateHolder()
    // a screen closed with Back forgets its state, so opening the same title again later starts fresh
    val seen = remember { HashSet<String>() }
    androidx.compose.runtime.SideEffect {
        if (alive.isNotEmpty()) {
            seen.filter { it !in alive }.forEach { holder.removeState(it) }
            seen.retainAll(alive.toSet()); seen.addAll(alive)
        }
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
      CompositionLocalProvider(LocalContentColor provides Color.White) {
        holder.SaveableStateProvider(screen.toString()) {
            when (screen) {
                AppScreen.Main -> MainScreen(nav)
                is AppScreen.Setup -> SetupScreen(nav, screen.first)
                is AppScreen.MovieDetails -> MovieScreen(nav, screen.key, screen.title)
                is AppScreen.ShowDetails -> ShowScreen(nav, screen.key)
                is AppScreen.Browse -> BrowseScreen(nav, screen.kind, screen.genre, showGenres = false, initialSort = screen.sort, title = screen.title)
                AppScreen.Other -> OtherScreen(nav)
                is AppScreen.Upcoming -> UpcomingScreen(nav, screen.key)
            }
        }
      }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(nav: Nav) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val tabFocus = remember { FocusRequester() }
    var barFocused by remember { mutableStateOf(false) }
    var anyFocus by remember { mutableStateOf(false) }
    val tabs = rememberSaveableStateHolder()
    val context = LocalContext.current
    // Nothing in focus shortly after the screen shows would make the remote look dead: start on the current tab.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(if (FocusMemory.returning() && FocusMemory.last.isNotEmpty()) 1100 else 300)
        if (!anyFocus) runCatching { tabFocus.requestFocus() }
    }
    // the selected tab keeps the focus when it changes under it (Back to the home tab)
    LaunchedEffect(tab) { if (barFocused) runCatching { tabFocus.requestFocus() } }
    // Back: from the content up to the tabs, from another tab to Home, and only from there out of the app.
    androidx.activity.compose.BackHandler(enabled = !barFocused || tab != Tab.Home) {
        if (!barFocused) {
            if (runCatching { tabFocus.requestFocus() }.isFailure) (context as? android.app.Activity)?.finish()
        } else {
            tabs.removeState(Tab.Home.name)
            tab = Tab.Home
        }
    }
    Column(Modifier.fillMaxSize().onFocusChanged { anyFocus = it.hasFocus }) {
        Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TeleStream", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
            Spacer(Modifier.width(36.dp))
            // coming up from the content always lands on the current tab, not on whichever tab happens to be above
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.onFocusChanged { barFocused = it.hasFocus }.focusProperties {
                    enter = { tabFocus }
                    exit = { if (it == androidx.compose.ui.focus.FocusDirection.Left || it == androidx.compose.ui.focus.FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
                }.focusGroup()) {
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    Surface(
                        onClick = {
                            // a tab opened from the bar starts fresh at its top; only Back returns to where you were
                            FocusMemory.last.clear()
                            GridCache.map.clear()
                            if (t == Tab.Home || t == tab) tabs.removeState(t.name)
                            tab = t
                        },
                        modifier = if (selected) Modifier.focusRequester(tabFocus) else Modifier,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) Color(0x33FFFFFF) else Color.Transparent,
                            contentColor = if (selected) Color.White else Muted,
                            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                        ),
                    ) {
                        Text(t.label, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // the time, and under it which TV this is (its name from the settings), like the web page's "המשך צפייה · סלון".
            // It may stick out of the bar a little, above and below, so the bar stays as low as it was.
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.wrapContentHeight(unbounded = true).offset(y = (-6).dp)) {
                Text(clockText(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD5D9E2))
                if (com.telestream.tv.Prefs.tvName.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("📺  " + com.telestream.tv.Prefs.tvName, fontSize = 13.sp, color = Muted,
                        modifier = Modifier.background(Color(0x1FFFFFFF), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 3.dp))
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            // each tab keeps what was chosen in it (genre, search text) while you visit another tab
            tabs.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.Home -> HomeTab(nav)
                    Tab.Movies -> BrowseScreen(nav, "movies", null, showGenres = true)
                    Tab.Shows -> BrowseScreen(nav, "shows", null, showGenres = true)
                    Tab.Search -> SearchTab(nav)
                    Tab.Settings -> SetupScreen(nav, first = false)
                }
            }
        }
    }
}
