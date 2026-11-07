package com.telestream.tv.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telestream.tv.Api
import com.telestream.tv.ContinueItem
import com.telestream.tv.Movie
import com.telestream.tv.PlayRequest
import com.telestream.tv.Show
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class HomeData(val continueItems: List<ContinueItem>, val myList: List<Any>, val movies: List<Movie>, val shows: List<Show>, val topMovies: List<Movie>)

@Composable
fun HomeTab(nav: Nav) {
    // Loads again each time the home tab shows, so what was just watched moves to the front
    val state = load("home") {
        coroutineScope {
            val cont = async { runCatching { Api.continueWatching() }.getOrDefault(emptyList()) }
            val list = async { runCatching { Api.myList() }.getOrDefault(emptyList()) }
            val movies = async { Api.movies(size = 30).items }
            val shows = async { Api.shows(size = 30).items }
            val top = async { Api.movies(size = 30, sort = "rating").items }
            HomeData(cont.await(), list.await(), movies.await(), shows.await(), top.await())
        }
    }
    Loading(state) { data ->
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
            if (data.continueItems.isNotEmpty()) {
                item { SectionTitle("המשך צפייה") }
                item {
                    CardRow(data.continueItems, key = { it.path }) { c ->
                        WideCard(
                            title = c.title, subtitle = c.label, image = c.still ?: c.fanart ?: c.poster,
                            progress = if (c.total > 0) (c.position / c.total).toFloat() else 0f,
                            tag = if (c.upNext) "הבא בתור" else "",
                            modifier = Modifier.restoreFocus("home", "c:" + c.path),
                        ) {
                            FocusMemory.last["home"] = "c:" + c.path
                            nav.play(PlayRequest(c.path, if (c.label.isBlank()) c.title else "${c.title} · ${c.label}", c.position))
                        }
                    }
                }
            }
            if (data.myList.isNotEmpty()) {
                item { SectionTitle("הרשימה שלי") }
                item { MixedRow(nav, "home", data.myList) }
            }
            item { SectionTitle("סרטים שנוספו לאחרונה") }
            item { MixedRow(nav, "home", data.movies) }
            item { SectionTitle("סדרות שנוספו לאחרונה") }
            item { MixedRow(nav, "home", data.shows) }
            item { SectionTitle("הסרטים עם הדירוג הגבוה") }
            item { MixedRow(nav, "home-top", data.topMovies) }
        }
    }
}

/** A row of movie and show posters; a click opens the title's page. */
@Composable
fun MixedRow(nav: Nav, screen: String, items: List<Any>) {
    CardRow(items, key = { if (it is Movie) "m:" + it.key else "s:" + (it as Show).key }) { item ->
        TitleCard(nav, screen, item)
    }
}

@Composable
fun TitleCard(nav: Nav, screen: String, item: Any) {
    when (item) {
        is Movie -> PosterCard(
            title = item.title, poster = item.poster, subtitle = listOfNotNull(item.year?.toString(), item.rating?.let { "★ %.1f".format(it) }).joinToString(" · "),
            progress = item.progress?.takeIf { !it.done }?.fraction ?: 0f, watched = item.progress?.done == true,
            modifier = Modifier.restoreFocus(screen, "m:" + item.key),
        ) {
            FocusMemory.last[screen] = "m:" + item.key
            nav.open(AppScreen.MovieDetails(item.key, item.title))
        }
        is Show -> PosterCard(
            title = item.title, poster = item.poster,
            subtitle = listOfNotNull(item.year?.toString(), if (item.seasonCount > 0) "${item.seasonCount} עונות" else null).joinToString(" · "),
            modifier = Modifier.restoreFocus(screen, "s:" + item.key),
        ) {
            FocusMemory.last[screen] = "s:" + item.key
            nav.open(AppScreen.ShowDetails(item.key))
        }
    }
}
