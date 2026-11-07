package com.telestream.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Loading, failed or ready; [reload] tries again. */
class Loaded<T>(val value: T?, val error: String?, val loading: Boolean, val reload: () -> Unit)

/** Runs [block] once per [key]; a value already in [Cache] shows at once while a fresh one loads. */
@Composable
fun <T> load(key: String, block: suspend () -> T): Loaded<T> {
    var attempt by remember(key) { mutableIntStateOf(0) }
    @Suppress("UNCHECKED_CAST")
    var value by remember(key) { mutableStateOf(Cache.values[key] as T?) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var loading by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key, attempt) {
        loading = true
        error = null
        try {
            val fresh = block()
            Cache.values[key] = fresh
            value = fresh
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        loading = false
    }
    return Loaded(value, error, loading) { attempt++ }
}

object Cache {
    val values = HashMap<String, Any?>()
}

/** The item last opened on each screen, so Back returns the focus to it. */
object FocusMemory {
    val last = HashMap<String, String>()
}

/** Focus this item when the screen comes back, if it was the one opened. */
@Composable
fun Modifier.restoreFocus(screen: String, id: String): Modifier {
    if (FocusMemory.last[screen] != id) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { requester.requestFocus() }
    }
    return this.focusRequester(requester)
}

@Composable
fun <T> Loading(state: Loaded<T>, content: @Composable (T) -> Unit) {
    val v = state.value
    when {
        v != null -> content(v)
        state.error != null -> ServerError(state.error, state.reload)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("טוען…", color = Muted, fontSize = 20.sp) }
    }
}

@Composable
fun ServerError(message: String, retry: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("השרת במחשב לא עונה", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("ודא שהמחשב דלוק ושהטלוויזיה על אותה רשת. ($message)", color = Muted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = retry, modifier = Modifier.focusRequester(focus)) { Text("נסה שוב") }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 48.dp, top = 18.dp, bottom = 10.dp))
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    if (fraction <= 0f) return
    Box(modifier.fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(Accent))
    }
}

/** A poster with its title under it; [badge] in the corner (e.g. watched). */
@Composable
fun PosterCard(
    title: String, poster: String?, subtitle: String = "", progress: Float = 0f, watched: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Column(Modifier.width(124.dp)) {
        Card(onClick = onClick, modifier = modifier.size(124.dp, 186.dp),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp))) {
            Box(Modifier.fillMaxSize().background(Panel)) {
                if (poster != null) {
                    AsyncImage(model = poster, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(title, modifier = Modifier.align(Alignment.Center).padding(10.dp), fontSize = 16.sp)
                }
                if (watched) {
                    Text("✓", modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(Accent, RoundedCornerShape(50)).padding(horizontal = 7.dp), color = Color.Black)
                }
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1, color = Muted, fontSize = 12.sp)
    }
}

/** A wide picture card, for "continue watching" and episodes. */
@Composable
fun WideCard(
    title: String, subtitle: String, image: String?, progress: Float = 0f, tag: String = "",
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Column(Modifier.width(224.dp)) {
        Card(onClick = onClick, modifier = modifier.size(224.dp, 126.dp), shape = CardDefaults.shape(RoundedCornerShape(10.dp))) {
            Box(Modifier.fillMaxSize().background(Panel)) {
                if (image != null) AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (tag.isNotBlank()) Text(tag, fontSize = 12.sp, color = Color.Black, modifier = Modifier.align(Alignment.TopStart)
                    .padding(6.dp).background(Accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun <T> CardRow(items: List<T>, key: (T) -> String, card: @Composable (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = key) { card(it) }
    }
}

fun minutes(seconds: Double): String {
    val s = seconds.toInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** In a text field on a TV remote: OK opens the keyboard, Up/Down move to the next control. */
fun leaveOrType(
    event: androidx.compose.ui.input.key.KeyEvent,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
    focusManager: androidx.compose.ui.focus.FocusManager,
): Boolean {
    if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
        return event.key == androidx.compose.ui.input.key.Key.DirectionCenter
    }
    return when (event.key) {
        androidx.compose.ui.input.key.Key.DirectionCenter, androidx.compose.ui.input.key.Key.Enter -> { keyboard?.show(); true }
        androidx.compose.ui.input.key.Key.DirectionDown -> { keyboard?.hide(); focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
        androidx.compose.ui.input.key.Key.DirectionUp -> { keyboard?.hide(); focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up) }
        else -> false
    }
}

private val hebrewMonths = listOf("בינואר", "בפברואר", "במרץ", "באפריל", "במאי", "ביוני", "ביולי", "באוגוסט", "בספטמבר", "באוקטובר", "בנובמבר", "בדצמבר")

/** "2021-03-12" → "12 במרץ 2021"; null when there is no usable date. */
fun hebrewDate(iso: String?): String? {
    val p = iso?.trim()?.take(10)?.split("-")?.takeIf { it.size == 3 } ?: return null
    val m = p[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return "${p[2].trimStart('0')} ${hebrewMonths[m - 1]} ${p[0]}"
}

/** Like the web page: "שודר לראשונה ב-…", or "ישודר ב-…" for a date still ahead. */
fun airedText(iso: String?): String? {
    val date = hebrewDate(iso) ?: return null
    val future = runCatching { java.time.LocalDate.parse(iso!!.trim().take(10)).isAfter(java.time.LocalDate.now()) }.getOrDefault(false)
    return (if (future) "ישודר ב-" else "שודר לראשונה ב-") + date
}
