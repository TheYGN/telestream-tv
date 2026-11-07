package com.telestream.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import com.telestream.tv.Api

/** All movies or all shows, newest first, a page at a time; with the genre chips on top in the main tabs. */
@Composable
fun BrowseScreen(nav: Nav, kind: String, initialGenre: String?, showGenres: Boolean) {
    var genre by rememberSaveable(kind) { mutableStateOf(initialGenre) }
    var sort by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    val home = load("homeinfo") { Api.home() }
    Column(Modifier.fillMaxSize()) {
        if (showGenres) {
            val genres = home.value?.let { if (kind == "movies") it.movieGenres else it.showGenres } ?: emptyList()
            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = sort == null && genre == null, onClick = { genre = null; sort = null }) { Text("הכי חדשים") } }
                item { FilterChip(selected = sort == "rating", onClick = { sort = if (sort == "rating") null else "rating" }) { Text("דירוג") } }
                items(genres) { g -> FilterChip(selected = genre == g, onClick = { genre = if (genre == g) null else g }) { Text(g) } }
            }
        } else if (initialGenre != null) {
            Text(initialGenre, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 8.dp))
        }
        PagedGrid(nav, kind, genre, sort, q = null, screen = "browse:$kind:$genre:$sort")
    }
}

/** A grid of posters that fetches the next page as the focus nears the end. */
@Composable
fun PagedGrid(nav: Nav, kind: String, genre: String?, sort: String?, q: String?, screen: String) {
    val items = remember(screen, q) { mutableStateListOf<Any>() }
    var page by remember(screen, q) { mutableStateOf(0) }
    var pages by remember(screen, q) { mutableStateOf(1) }
    var loading by remember(screen, q) { mutableStateOf(false) }
    var error by remember(screen, q) { mutableStateOf<String?>(null) }
    var want by remember(screen, q) { mutableStateOf(1) }

    LaunchedEffect(screen, q, want) {
        if (page >= want || page >= pages || loading) return@LaunchedEffect
        loading = true
        error = null
        try {
            val next = page + 1
            val result = if (kind == "movies") Api.movies(next, genre, q, sort = sort).let { it.items to it.pages }
            else Api.shows(next, genre, q, sort = sort).let { it.items to it.pages }
            items.addAll(result.first)
            pages = result.second
            page = next
        } catch (e: Exception) {
            error = e.message ?: "error"
        }
        loading = false
    }

    when {
        items.isEmpty() && error != null -> ServerError(error!!) { want = page + 1; error = null }
        items.isEmpty() && (loading || page == 0) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("טוען…", color = Muted) }
        items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("לא נמצא כלום", color = Muted, fontSize = 20.sp) }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(124.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(items, key = { i, it -> (if (it is com.telestream.tv.Movie) "m:" + it.key else "s:" + (it as com.telestream.tv.Show).key) + "#" + i }) { index, item ->
                if (index >= items.size - 12 && page < pages) LaunchedEffect(page) { want = page + 1 }
                TitleCard(nav, screen, item)
            }
        }
    }
}
