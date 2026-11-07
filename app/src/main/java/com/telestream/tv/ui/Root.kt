package com.telestream.tv.ui

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
fun Root(screen: AppScreen, open: (AppScreen) -> Unit, replace: (AppScreen) -> Unit) {
    val context = LocalContext.current
    val nav = Nav(open, replace) { PlayerActivity.start(context, it) }
    val holder = rememberSaveableStateHolder()
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

@Composable
fun MainScreen(nav: Nav) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (FocusMemory.last.isEmpty()) { kotlinx.coroutines.delay(300); runCatching { tabFocus.requestFocus() } } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TeleStream", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
            Spacer(Modifier.width(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    Surface(
                        onClick = { tab = t },
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
            // which TV this is (its name from the settings), like the web page's "המשך צפייה · סלון"
            if (com.telestream.tv.Prefs.tvName.isNotBlank()) {
                Text("📺  " + com.telestream.tv.Prefs.tvName, fontSize = 15.sp, color = Muted,
                    modifier = Modifier.background(Color(0x1FFFFFFF), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        Box(Modifier.fillMaxSize()) {
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
