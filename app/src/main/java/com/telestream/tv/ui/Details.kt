package com.telestream.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.telestream.tv.Api
import com.telestream.tv.Episode
import com.telestream.tv.MediaFile
import com.telestream.tv.Movie
import com.telestream.tv.PlayRequest
import com.telestream.tv.Show
import com.telestream.tv.fileLabel
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import com.telestream.tv.Actor
import com.telestream.tv.TrailerActivity

/** The big picture behind a title's page, darkened towards the text. */
@Composable
fun Backdrop(image: String?) {
    Box(Modifier.fillMaxSize()) {
        if (image != null) AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x33000000), Bg.copy(alpha = 0.92f), Bg))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Bg))))
    }
}

@Composable
fun MetaLine(year: Int?, rating: Double?, genres: List<String>, extra: String = "") {
    val parts = listOfNotNull(year?.toString(), rating?.let { "★ %.1f".format(it) }, extra.ifBlank { null }, genres.take(3).joinToString(" · ").ifBlank { null })
    Text(parts.joinToString("   "), color = Muted, fontSize = 16.sp)
}

/** Several copies of the same video: ask which one; one copy plays at once. */
@Composable
fun FileChooser(files: List<MediaFile>, onPick: (MediaFile) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
      androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides androidx.compose.ui.graphics.Color.White) {
        Column(Modifier.background(Panel, RoundedCornerShape(14.dp)).padding(24.dp).width(560.dp)) {
            Text("באיזה קובץ לנגן?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            files.forEachIndexed { i, f ->
                Surface(
                    onClick = { onPick(f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).let { if (i == 0) it.focusRequester(first) else it },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), contentColor = Color.White,
                        focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                ) { Text(fileLabel(f), modifier = Modifier.padding(12.dp), maxLines = 2, fontSize = 15.sp) }
            }
        }
      }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
fun MovieScreen(nav: Nav, key: String, title: String) {
    // There is no single-movie address on the server: search by the title and pick the same key,
    // which also brings this TV's latest progress in it
    val state = load("movie:$key") {
        Api.movies(q = title, size = 100).items.firstOrNull { it.key == key } ?: cachedMovie(key) ?: throw IllegalStateException("not found")
    }
    Loading(state) { movie -> MovieDetails(nav, movie) }
}

private fun cachedMovie(key: String): Movie? {
    Cache.values.values.forEach { v ->
        (v as? HomeData)?.let { h -> (h.movies + h.topMovies + h.myList.filterIsInstance<Movie>()).firstOrNull { it.key == key }?.let { return it } }
        (v as? SearchResults)?.movies?.firstOrNull { it.key == key }?.let { return it }
    }
    return null
}

@Composable
fun MovieDetails(nav: Nav, movie: Movie) {
    val scope = rememberCoroutineScope()
    var choose by remember { mutableStateOf<Double?>(null) }
    var inList by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(movie.key) {
        inList = runCatching { Api.myList().any { it is Movie && it.key == movie.key } }.getOrNull()
    }
    val playFocus = remember { FocusRequester() }
    fun play(start: Double) {
        if (movie.files.size > 1) choose = start
        else movie.files.firstOrNull()?.let { nav.play(PlayRequest(it.path, movie.title, start)) }
    }
    Box(Modifier.fillMaxSize()) {
        Backdrop(movie.fanart ?: movie.poster)
      LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
       item {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (movie.poster != null) {
                AsyncImage(movie.poster, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(220.dp, 330.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(36.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(movie.title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                if (movie.originalTitle.isNotBlank() && movie.originalTitle != movie.title) Text(movie.originalTitle, color = Muted, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                val runtime = movie.files.firstOrNull()?.duration?.takeIf { it > 0 }?.let { "${(it / 60).toInt()} דק׳" } ?: ""
                MetaLine(movie.year, movie.rating, movie.genres, runtime)
                Spacer(Modifier.height(14.dp))
                Text(movie.overview, fontSize = 17.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val p = movie.progress
                    if (p != null && !p.done && p.position > 30) {
                        Button(onClick = { play(p.position) }, modifier = Modifier.focusRequester(playFocus)) { Text("▶  המשך מ-${minutes(p.position)}") }
                        OutlinedButton(onClick = { play(0.0) }) { Text("מההתחלה") }
                    } else {
                        Button(onClick = { play(0.0) }, modifier = Modifier.focusRequester(playFocus)) { Text("▶  נגן") }
                    }
                    if (inList != null) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    if (inList == true) Api.removeFromList("movie", movie.key) else Api.addToList("movie", movie.key)
                                    inList = !inList!!
                                }
                            }
                        }) { Text(if (inList == true) "✓ ברשימה שלי" else "+ הרשימה שלי") }
                    }
                    TrailerButton("movie", movie.tmdbId)
                }
                if (movie.files.size > 1) Text("${movie.files.size} עותקים של הסרט", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
       }
       item { CastRow("movie", movie.tmdbId) }
      }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    choose?.let { start ->
        FileChooser(movie.files, onPick = { f -> choose = null; nav.play(PlayRequest(f.path, movie.title, start)) }, onDismiss = { choose = null })
    }
}

@Composable
fun ShowScreen(nav: Nav, key: String) {
    val state = load("show:$key") { Api.show(key) }
    Loading(state) { detail ->
        val show = detail.show
        val seasons = detail.seasons
        // Start on the season this TV is in the middle of: the last one with anything watched
        val startSeason = seasons.lastOrNull { s -> s.episodes.any { it.progress != null } }?.number ?: seasons.firstOrNull()?.number ?: 1
        var seasonNumber by rememberSaveable(key) { mutableStateOf(startSeason) }
        var choose by remember { mutableStateOf<Pair<Episode, Double>?>(null) }
        val season = seasons.firstOrNull { it.number == seasonNumber } ?: seasons.firstOrNull()
        val scope = rememberCoroutineScope()
        var inList by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(key) { inList = runCatching { Api.myList().any { it is Show && it.key == key } }.getOrNull() }

        fun play(e: Episode, start: Double) {
            val label = "${show.title} · עונה ${season?.number} פרק ${e.number}" + if (e.name.isNotBlank()) " · ${e.name}" else ""
            FocusMemory.last["show:$key"] = "e:${season?.number}:${e.number}"
            if (e.files.size > 1) choose = e to start else e.files.firstOrNull()?.let { nav.play(PlayRequest(it.path, label, start)) }
        }
        // The episode to continue from: the first started-but-unfinished, else the first unwatched
        val resume = season?.episodes?.firstOrNull { it.progress?.let { p -> !p.done && p.position > 30 } == true }
            ?: season?.episodes?.firstOrNull { it.progress?.done != true }
        val firstFocus = remember { FocusRequester() }
        var focusedEpisode by remember { mutableStateOf<Episode?>(null) }

        Box(Modifier.fillMaxSize()) {
            Backdrop(show.fanart ?: show.poster)
            LazyColumn(contentPadding = PaddingValues(top = 40.dp, bottom = 48.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 48.dp).fillMaxWidth(0.6f)) {
                        Text(show.title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        MetaLine(show.year, show.rating, show.genres, "${seasons.size} עונות")
                        Spacer(Modifier.height(10.dp))
                        Text(show.overview, fontSize = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (resume != null) {
                                val p = resume.progress
                                val resumeAt = if (p != null && !p.done) p.position else 0.0
                                Button(onClick = { play(resume, resumeAt) }, modifier = Modifier.focusRequester(firstFocus)) {
                                    Text((if (resumeAt > 30) "▶  המשך" else "▶  נגן") + " פרק ${resume.number}")
                                }
                            }
                            if (inList != null) OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        if (inList == true) Api.removeFromList("show", key) else Api.addToList("show", key)
                                        inList = !inList!!
                                    }
                                }
                            }) { Text(if (inList == true) "✓ ברשימה שלי" else "+ הרשימה שלי") }
                            TrailerButton("tv", show.tmdbId)
                        }
                    }
                }
                if (seasons.size > 1) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { it.number }) { s ->
                            FilterChip(selected = s.number == season?.number, onClick = { seasonNumber = s.number }) { Text("עונה ${s.number}") }
                        }
                    }
                } else item { Spacer(Modifier.height(16.dp)) }
                if (season != null) item {
                    CardRow(season.episodes, key = { "${season.number}:${it.number}" }) { e ->
                        WideCard(
                            title = "${e.number}. " + e.name.ifBlank { "פרק ${e.number}" },
                            subtitle = hebrewDate(e.aired) ?: e.overview,
                            image = e.still ?: season.poster ?: show.fanart,
                            progress = e.progress?.takeIf { !it.done }?.fraction ?: 0f,
                            tag = if (e.progress?.done == true) "✓ נצפה" else "",
                            modifier = Modifier.restoreFocus("show:$key", "e:${season.number}:${e.number}")
                                .onFocusChanged { if (it.isFocused) focusedEpisode = e },
                        ) {
                            val p = e.progress
                            play(e, if (p != null && !p.done) p.position else 0.0)
                        }
                    }
                }
                // the episode in focus: its name, when it first aired, length and full summary
                if (season != null) item {
                    val e = focusedEpisode?.takeIf { f -> season.episodes.any { it === f } } ?: resume ?: season.episodes.firstOrNull()
                    if (e != null) EpisodeInfo(season.number, e)
                }
                item { CastRow("tv", show.tmdbId) }
            }
        }
        LaunchedEffect(Unit) { if (FocusMemory.last["show:$key"] == null) runCatching { firstFocus.requestFocus() } }
        choose?.let { (e, start) ->
            FileChooser(e.files, onPick = { f ->
                choose = null
                nav.play(PlayRequest(f.path, "${show.title} · עונה ${season?.number} פרק ${e.number}", start))
            }, onDismiss = { choose = null })
        }
    }
}

/** Shown only when the server finds a trailer for this title. */
@Composable
fun TrailerButton(kind: String, tmdbId: Int?) {
    if (tmdbId == null) return
    val context = LocalContext.current
    val state = load("trailer:$kind:$tmdbId") { Api.trailer(kind, tmdbId) ?: "" }
    val key = state.value?.ifBlank { null } ?: return
    OutlinedButton(onClick = { TrailerActivity.open(context, key) }) { Text("🎬  טריילר") }
}

/** The main actors, with their picture and role. */
@Composable
fun CastRow(kind: String, tmdbId: Int?) {
    if (tmdbId == null) return
    val state = load("cast:$kind:$tmdbId") { Api.cast(kind, tmdbId) }
    val cast = state.value?.takeIf { it.isNotEmpty() } ?: return
    Column {
        SectionTitle("שחקנים")
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cast.size) { i -> ActorCard(cast[i]) }
        }
    }
}

@Composable
fun ActorCard(actor: Actor) {
    Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = {},
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Panel, focusedContainerColor = Panel),
            border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White))),
            modifier = Modifier.size(96.dp),
        ) {
            if (actor.photo != null) AsyncImage(actor.photo, actor.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(actor.name.take(1), fontSize = 32.sp, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(6.dp))
        Text(actor.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (actor.role.isNotBlank()) Text(actor.role, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EpisodeInfo(season: Int, e: Episode) {
    Column(Modifier.padding(start = 48.dp, end = 48.dp, top = 14.dp).fillMaxWidth(0.7f)) {
        Text(e.name.ifBlank { "פרק ${e.number}" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        val runtime = e.files.firstOrNull()?.duration?.takeIf { it > 0 }?.let { "${(it / 60).toInt()} דק׳" }
        Text(listOfNotNull("עונה $season · פרק ${e.number}", airedText(e.aired), runtime).joinToString("   ·   "), color = Muted, fontSize = 15.sp)
        if (e.overview.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(e.overview, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
        }
    }
}
