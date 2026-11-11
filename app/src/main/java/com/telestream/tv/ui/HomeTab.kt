package com.telestream.tv.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telestream.tv.Api
import com.telestream.tv.ContinueItem
import com.telestream.tv.Movie
import com.telestream.tv.PlayRequest
import com.telestream.tv.Show
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.telestream.tv.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class HomeData(
    val continueItems: List<ContinueItem>, val myList: List<Any>, val movies: List<Movie>, val shows: List<Show>, val topMovies: List<Movie>,
    val movieGenres: List<String>, val showGenres: List<String>,
)

/** A row on the home page: its title, what "הכל" opens, and how to load it. */
private class Rail(val title: String, val all: AppScreen, val load: suspend () -> List<Any>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTab(nav: Nav) {
    // Loads again each time the home tab shows, so what was just watched moves to the front
    val state = load("home", live = true) {
        coroutineScope {
            val cont = async { runCatching { Api.continueWatching() }.getOrDefault(emptyList()) }
            val list = async { runCatching { Api.myList() }.getOrDefault(emptyList()) }
            val movies = async { Api.movies(size = 24).items }
            val shows = async { Api.shows(size = 24).items }
            val top = async { Api.movies(size = 24, sort = "rating").items }
            val info = async { runCatching { Api.home() }.getOrNull() }
            HomeData(cont.await(), list.await(), movies.await(), shows.await(), top.await(),
                info.await()?.movieGenres?.take(6) ?: emptyList(), info.await()?.showGenres?.take(2) ?: emptyList())
        }
    }
    val soon = load("upcoming") { runCatching { Api.upcoming() }.getOrDefault(emptyList()) }
    Loading(state) { data ->
        val genreRails = data.movieGenres.map { g -> Rail(g, AppScreen.Browse("movies", g)) { Api.movies(size = 24, genre = g).items } } +
            data.showGenres.map { g -> Rail("סדרות · $g", AppScreen.Browse("shows", g, title = "סדרות · $g")) { Api.shows(size = 24, genre = g).items } }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val heroScroll = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
        // The stock TV behaviour puts whatever takes the focus 30% down the screen. For the banner's buttons that
        // cut the banner off, and a second scroll then brought it back: two movements for one key. So the banner
        // is scrolled to as a whole, in one movement; the rows below keep the stock behaviour.
        val rowScroll = LocalBringIntoViewSpec.current
        val pageScroll = remember(rowScroll) { PageScroll(rowScroll) }
        // inside a row, sideways scrolling stays as it was
        fun LazyListScope.row(key: String, content: @Composable () -> Unit) = item(key = key) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowScroll, content = content)
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides pageScroll) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp)) {
            val picks = data.movies.filter { it.fanart != null && it.overview.isNotBlank() }.take(6)
            if (picks.isNotEmpty()) item(key = "hero") {
                // coming back up to the banner: show all of it, not just its buttons
                HeroBanner(nav, picks) { has ->
                    // the banner's buttons don't place themselves (see PageScroll): the whole banner comes into view
                    pageScroll.banner = has
                    heroScroll[0]?.cancel()
                    heroScroll[0] = if (has) scope.launch { listState.animateScrollToItem(0) } else null
                }
            }
            val upcoming = soon.value.orEmpty()
            if (upcoming.isNotEmpty()) {
                item(key = "t:soon") { SectionTitle("בקרוב ברשת") }
                row("r:soon") {
                    CardRow(upcoming, key = { it.key }) { u ->
                        PosterCard(title = u.title, poster = u.poster, subtitle = u.whenText,
                            modifier = Modifier.restoreFocus("home", "u:" + u.key)) {
                            FocusMemory.last["home"] = "u:" + u.key
                            val m = u.movie
                            if (m != null) nav.open(AppScreen.MovieDetails(m.key, m.title)) else nav.open(AppScreen.Upcoming(u.key))
                        }
                    }
                }
            }
            if (data.continueItems.isNotEmpty()) {
                item(key = "t:cont") { SectionTitle("המשך צפייה · ${Prefs.tvName}") }
                row("r:cont") {
                    CardRow(data.continueItems, key = { it.path }) { c ->
                        WideCard(
                            title = c.title,
                            subtitle = listOfNotNull(c.label.ifBlank { null }, if (!c.upNext && c.total > 0) "נשארו ${minutes(c.total - c.position)}" else null).joinToString(" · "),
                            image = c.still ?: c.fanart ?: c.poster,
                            progress = if (c.total > 0) (c.position / c.total).toFloat() else 0f,
                            tag = if (c.upNext) "הבא בתור" else "",
                            modifier = Modifier.restoreFocus("home", "c:" + c.path),
                        ) {
                            FocusMemory.last["home"] = "c:" + c.path
                            // like the web page: the next episode from the start, otherwise a few seconds back
                            val start = if (c.upNext) 0.0 else (c.position - 5).coerceAtLeast(0.0)
                            nav.play(PlayRequest(c.path, if (c.label.isBlank()) c.title else "${c.title} · ${c.label}", start, c.kind, c.key))
                        }
                    }
                }
            }
            if (data.myList.isNotEmpty()) {
                item(key = "t:list") { SectionTitle("הרשימה שלי") }
                row("r:list") { MixedRow(nav, "home", data.myList, row = "list") }
            }
            item(key = "t:movies") { SectionTitle("נוספו לאחרונה · סרטים") }
            row("r:movies") { MixedRow(nav, "home", data.movies, row = "movies", all = AppScreen.Browse("movies", null, title = "סרטים")) }
            item(key = "t:shows") { SectionTitle("נוספו לאחרונה · סדרות") }
            row("r:shows") { MixedRow(nav, "home", data.shows, row = "shows", all = AppScreen.Browse("shows", null, title = "סדרות")) }
            item(key = "t:top") { SectionTitle("הסרטים הכי מדורגים") }
            row("r:top") { MixedRow(nav, "home", data.topMovies, row = "top", all = AppScreen.Browse("movies", null, sort = "rating", title = "הסרטים הכי מדורגים")) }
            genreRails.forEach { rail ->
                row("rail:" + rail.title) { LazyRail(nav, rail) }
            }
        }
        }
    }
}

/** The home page's up-and-down scrolling: as usual, except that the banner's buttons don't place themselves. */
@OptIn(ExperimentalFoundationApi::class)
private class PageScroll(val base: BringIntoViewSpec) : BringIntoViewSpec {
    /** The focus is in the banner, which is scrolled to as a whole. */
    var banner = false
    override val scrollAnimationSpec get() = base.scrollAnimationSpec
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (banner) 0f else base.calculateScrollDistance(offset, size, containerSize)
}

/** A genre row: loads on its own, so a slow or failing row doesn't hold up the others (and hides itself if empty). */
@Composable
private fun LazyRail(nav: Nav, rail: Rail) {
    val state = load("home:rail:" + rail.title, maxAgeMs = 5 * 60_000L) { rail.load() }
    val items = state.value
    Column(Modifier.heightIn(min = if (items == null) 260.dp else 0.dp)) {
        if (items == null || items.isNotEmpty()) SectionTitle(rail.title)
        if (!items.isNullOrEmpty()) MixedRow(nav, "home", items, row = "rail:" + rail.title, all = rail.all)
    }
}

/** The big rotating picture at the top of home: recently added movies, like the web library page. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HeroBanner(nav: Nav, picks: List<Movie>, onFocusChange: (Boolean) -> Unit = {}) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val movie = picks[index % picks.size]
    LaunchedEffect(index, focused) {
        if (!focused) { delay(8000); index = (index + 1) % picks.size }
    }
    // fetch the next picture ahead of time, so the change fades between two pictures and not into an empty frame
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(index) {
        picks[(index + 1) % picks.size].fanart?.let {
            coil.Coil.imageLoader(context).enqueue(coil.request.ImageRequest.Builder(context).data(it).build())
        }
    }
    Box(Modifier.fillMaxWidth().height(380.dp).onFocusChanged { if (it.hasFocus != focused) { focused = it.hasFocus; onFocusChange(it.hasFocus) } }) {
        Crossfade(targetState = movie.fanart, animationSpec = tween(900), label = "hero") { img ->
            AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.55f), Bg.copy(alpha = 0.95f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Bg))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 28.dp).widthIn(max = 560.dp)) {
            Text("נוסף לאחרונה", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(movie.title, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (movie.originalTitle.isNotBlank() && movie.originalTitle != movie.title) Text(movie.originalTitle, color = Muted, fontSize = 16.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            MetaLine(movie.year, movie.rating, movie.genres)
            Spacer(Modifier.height(8.dp))
            Text(movie.overview, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp, color = Color(0xFFD5D9E2))
            Spacer(Modifier.height(14.dp))
            // from above or from below, the focus comes in on "נגן" (it used to depend on what was straight above or below);
            // coming back from the title's page it returns to "פרטים", where it left
            val play = remember { FocusRequester() }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusProperties { enter = { if (FocusMemory.returning()) FocusRequester.Default else play } }.keepSideways()) {
                Button(modifier = Modifier.focusRequester(play), onClick = {
                    val p = movie.progress?.takeIf { !it.done }?.position ?: 0.0
                    movie.files.firstOrNull()?.let { nav.play(PlayRequest(it.path, movie.title, p, "movie", movie.key, movie.overview)) }
                }) { Text("▶  נגן") }
                OutlinedButton(onClick = {
                    FocusMemory.last["home"] = "hero"
                    Cache.values.getOrPut("movie:" + movie.key) { movie }
                    nav.open(AppScreen.MovieDetails(movie.key, movie.title))
                }, modifier = Modifier.restoreFocus("home", "hero")) { Text("פרטים") }
                Spacer(Modifier.width(18.dp))
                // dots; left/right past the buttons isn't needed — it rotates by itself, and these show where it is
                picks.indices.forEach { i ->
                    Box(Modifier.padding(horizontal = 3.dp).size(width = if (i == index % picks.size) 22.dp else 8.dp, height = 8.dp)
                        .background(if (i == index % picks.size) Color.White else Color(0x4DFFFFFF), RoundedCornerShape(50)))
                }
            }
        }
    }
}

/** A row of movie and show posters; a click opens the title's page. */
@Composable
fun MixedRow(nav: Nav, screen: String, items: List<Any>, row: String = "", all: AppScreen? = null) {
    val withAll: List<Any> = if (all != null) items + all else items
    CardRow(withAll, key = { when (it) { is Movie -> "m:" + it.key; is Show -> "s:" + it.key; else -> "all" } }) { item ->
        if (item is AppScreen) AllCard(Modifier.restoreFocus(screen, "$row|all")) {
            FocusMemory.last[screen] = "$row|all"
            GridCache.map.clear() // "הכל" opens from the top, with what the server has now
            nav.open(item)
        } else TitleCard(nav, screen, item, row)
    }
}

/** The "הכל ›" card at the end of a row. */
@Composable
fun AllCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.size(124.dp, 186.dp), shape = CardDefaults.shape(RoundedCornerShape(10.dp)), border = cardBorder()) {
        Box(Modifier.fillMaxSize().background(Panel), contentAlignment = Alignment.Center) {
            Text("הכל ›", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TitleCard(nav: Nav, screen: String, item: Any, row: String = "") {
    // [row] tells apart the same title shown in two rows of one screen, so Back returns to the row it was opened from
    when (item) {
        is Movie -> PosterCard(
            title = item.title, poster = item.poster, subtitle = listOfNotNull(item.year?.toString(), item.rating?.let { "★ %.1f".format(it) }).joinToString(" · "),
            progress = item.progress?.takeIf { !it.done }?.fraction ?: 0f, watched = item.progress?.done == true,
            modifier = Modifier.restoreFocus(screen, "$row|m:" + item.key),
        ) {
            FocusMemory.last[screen] = "$row|m:" + item.key
            // the page opens at once with what the card already knows; the fresh copy replaces it when it arrives
            Cache.values.getOrPut("movie:" + item.key) { item }
            nav.open(AppScreen.MovieDetails(item.key, item.title))
        }
        is Show -> PosterCard(
            title = item.title, poster = item.poster,
            subtitle = listOfNotNull(item.year?.toString(), if (item.seasonCount > 0) "${item.seasonCount} עונות" else null).joinToString(" · "),
            modifier = Modifier.restoreFocus(screen, "$row|s:" + item.key),
        ) {
            FocusMemory.last[screen] = "$row|s:" + item.key
            nav.open(AppScreen.ShowDetails(item.key))
        }
    }
}
